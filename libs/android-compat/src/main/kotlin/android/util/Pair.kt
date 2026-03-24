package android.util

open class Pair<F, S>(val first: F, val second: S) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Pair<*, *>) return false
        return first == other.first && second == other.second
    }

    override fun hashCode(): Int {
        return (first?.hashCode() ?: 0) * 31 + (second?.hashCode() ?: 0)
    }

    override fun toString(): String = "Pair($first, $second)"

    companion object {
        @JvmStatic
        fun <A, B> create(a: A, b: B): Pair<A, B> = Pair(a, b)
    }
}
