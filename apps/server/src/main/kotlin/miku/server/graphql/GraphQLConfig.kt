package miku.server.graphql

import com.expediagroup.graphql.generator.SchemaGeneratorConfig
import com.expediagroup.graphql.generator.TopLevelObject
import com.expediagroup.graphql.generator.hooks.SchemaGeneratorHooks
import com.expediagroup.graphql.generator.toSchema
import graphql.GraphQL
import graphql.Scalars
import graphql.schema.GraphQLType
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass
import kotlin.reflect.KType

@Serializable
private data class GraphQLRequest(
    val query: String,
    val operationName: String? = null,
    val variables: Map<String, kotlinx.serialization.json.JsonElement>? = null,
)

private val gqlJson = Json { ignoreUnknownKeys = true; isLenient = true }

fun Application.configureGraphQL() {
    if (System.getProperty("miku.test") == "true") return

    val config = SchemaGeneratorConfig(
        supportedPackages = listOf("miku.server.graphql", "miku.domain.model"),
        hooks = CustomSchemaHooks(),
    )

    val schema = toSchema(
        config = config,
        queries = listOf(
            TopLevelObject(SourceQuery()),
            TopLevelObject(MangaQuery()),
            TopLevelObject(LibraryQuery()),
            TopLevelObject(ExtensionQuery()),
        ),
        mutations = listOf(
            TopLevelObject(LibraryMutation()),
            TopLevelObject(AuthMutation()),
        ),
    )

    val graphQL = GraphQL.newGraphQL(schema).build()

    routing {
        post("/api/graphql") {
            val body = call.receiveText()
            val request = gqlJson.decodeFromString<GraphQLRequest>(body)

            val variablesMap = request.variables?.mapValues { (_, v) ->
                // Convert JsonElement to Any for graphql-java
                v.toString().let { str ->
                    when {
                        str == "null" -> null
                        str.startsWith("\"") -> str.trim('"')
                        str == "true" -> true
                        str == "false" -> false
                        str.contains('.') -> str.toDoubleOrNull() ?: str
                        else -> str.toLongOrNull() ?: str
                    }
                }
            } ?: emptyMap()

            val result = graphQL.execute(
                graphql.ExecutionInput.newExecutionInput()
                    .query(request.query)
                    .operationName(request.operationName)
                    .variables(variablesMap)
                    .build()
            )

            call.respond(HttpStatusCode.OK, result.toSpecification())
        }

        get("/graphiql") {
            call.respondText(GRAPHIQL_HTML, ContentType.Text.Html)
        }
    }
}

class CustomSchemaHooks : SchemaGeneratorHooks {
    override fun willGenerateGraphQLType(type: KType): GraphQLType? {
        return when (type.classifier as? KClass<*>) {
            Long::class -> Scalars.GraphQLString
            Instant::class -> Scalars.GraphQLString
            else -> null
        }
    }
}

private val GRAPHIQL_HTML = """
<!DOCTYPE html>
<html><head><title>Miku GraphiQL</title>
<link href="https://unpkg.com/graphiql/graphiql.min.css" rel="stylesheet" />
</head><body style="margin:0;">
<div id="graphiql" style="height:100vh;"></div>
<script crossorigin src="https://unpkg.com/react/umd/react.production.min.js"></script>
<script crossorigin src="https://unpkg.com/react-dom/umd/react-dom.production.min.js"></script>
<script crossorigin src="https://unpkg.com/graphiql/graphiql.min.js"></script>
<script>
  ReactDOM.render(
    React.createElement(GraphiQL, {
      fetcher: GraphiQL.createFetcher({ url: '/api/graphql' }),
    }),
    document.getElementById('graphiql'),
  );
</script>
</body></html>
""".trimIndent()
