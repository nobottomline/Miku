package miku.server.service

import de.mkammerer.argon2.Argon2Factory
import miku.domain.model.User
import miku.domain.model.UserRole
import miku.domain.repository.UserRepository
import miku.domain.service.AuthService
import miku.domain.service.TokenPair
import miku.server.ServerConfig
import miku.server.config.AuthenticationException

class MikuAuthService(
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
    private val config: ServerConfig,
) : AuthService {

    private val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)

    private fun hashPassword(password: String): String {
        return argon2.hash(
            config.argon2Iterations,
            config.argon2Memory,
            config.argon2Parallelism,
            password.toCharArray(),
        )
    }

    private fun verifyPassword(password: String, hash: String): Boolean {
        return argon2.verify(hash, password.toCharArray())
    }

    override suspend fun register(username: String, email: String, password: String): User {
        // Validation
        require(username.length in 3..50) { "Username must be between 3 and 50 characters" }
        require(username.matches(Regex("^[a-zA-Z0-9_-]+$"))) { "Username can only contain letters, numbers, hyphens, and underscores" }
        require(email.matches(Regex("^[\\w-.]+@[\\w-]+\\.[a-zA-Z]{2,}$"))) { "Invalid email format" }
        require(password.length >= 8) { "Password must be at least 8 characters" }
        require(password.any { it.isUpperCase() } && password.any { it.isDigit() }) {
            "Password must contain at least one uppercase letter and one digit"
        }

        // Check uniqueness
        userRepository.findByUsername(username)?.let {
            throw IllegalArgumentException("Username already taken")
        }
        userRepository.findByEmail(email)?.let {
            throw IllegalArgumentException("Email already registered")
        }

        val passwordHash = hashPassword(password)

        // First user gets ADMIN role
        val role = if (userRepository.countUsers() == 0L) UserRole.ADMIN else UserRole.USER

        return userRepository.create(
            User(
                username = username,
                email = email,
                passwordHash = passwordHash,
                role = role,
            )
        )
    }

    override suspend fun login(username: String, password: String): TokenPair {
        val user = userRepository.findByUsername(username)
            ?: throw AuthenticationException("Invalid credentials")

        if (!user.isActive) {
            throw AuthenticationException("Account is deactivated")
        }

        if (!verifyPassword(password, user.passwordHash)) {
            throw AuthenticationException("Invalid credentials")
        }

        userRepository.updateLastLogin(user.id)

        return TokenPair(
            accessToken = jwtService.generateAccessToken(user),
            refreshToken = jwtService.generateRefreshToken(user),
            expiresIn = config.jwtAccessExpireMinutes * 60,
        )
    }

    override suspend fun refreshToken(refreshToken: String): TokenPair {
        if (!jwtService.isRefreshToken(refreshToken)) {
            throw AuthenticationException("Invalid refresh token")
        }

        val userId = jwtService.validateToken(refreshToken)
            ?: throw AuthenticationException("Invalid or expired refresh token")

        val user = userRepository.findById(userId)
            ?: throw AuthenticationException("User not found")

        if (!user.isActive) {
            throw AuthenticationException("Account is deactivated")
        }

        return TokenPair(
            accessToken = jwtService.generateAccessToken(user),
            refreshToken = jwtService.generateRefreshToken(user),
            expiresIn = config.jwtAccessExpireMinutes * 60,
        )
    }

    override suspend fun validateToken(token: String): User? {
        val userId = jwtService.validateToken(token) ?: return null
        return userRepository.findById(userId)
    }

    override suspend fun changePassword(userId: Long, oldPassword: String, newPassword: String) {
        val user = userRepository.findById(userId)
            ?: throw AuthenticationException("User not found")

        if (!verifyPassword(oldPassword, user.passwordHash)) {
            throw AuthenticationException("Current password is incorrect")
        }

        require(newPassword.length >= 8) { "New password must be at least 8 characters" }

        val newHash = hashPassword(newPassword)
        userRepository.update(user.copy(passwordHash = newHash))
    }
}
