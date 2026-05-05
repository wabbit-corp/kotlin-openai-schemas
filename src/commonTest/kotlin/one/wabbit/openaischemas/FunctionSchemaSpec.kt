// SPDX-License-Identifier: LicenseRef-Wabbit-Public-Test-License-1.1

package one.wabbit.openaischemas

import kotlin.String as KString
import kotlin.String
import kotlin.collections.List as KList
import kotlin.test.Test
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import one.wabbit.openaischemas.FunctionSchema.Doc

class FunctionSchemaSpec {
    //    @Serializable sealed interface Request {
    //        @Doc("Get the current weather in a location.")
    //        @SerialName("GetCurrentWeather")
    //        @Serializable data class GetCurrentWeather(val longitude: Double, val latitude:
    // Double): Request
    //
    //        @SerialName("GetDiscordUserInfo")
    //        @Serializable data class GetDiscordUserInfo(
    //            val userId: String?, val userName: String?, val includeUserAvatar: KBoolean):
    // Request
    //
    //        @SerialName("SearchDiscordMessages")
    //        @Serializable data class SearchDiscordMessages(
    //            val keywords: KList<String>, val channelId: ULong?, val limit: KInt?): Request
    //
    //        @SerialName("GetDiscordChannelInfo")
    //        @Serializable data class GetDiscordChannelInfo(
    //            val channelId: ULong?): Request
    //
    //        @Doc("Generate an image from a prompt.")
    //        @SerialName("GenerateImage")
    //        @Serializable data class GenerateImage(
    //            @Doc("A prompt to generate an image from.")
    //            val prompt: String): Request
    //
    //        @Doc("Generate a meme given a template and text.")
    //        @SerialName("GenerateMeme")
    //        @Serializable data class GenerateMeme(
    //            @Doc("The name of the meme template on Imgflip.")
    //            val templateName: String,
    //            @Doc("The text to put in each box of the meme.")
    //            val boxText: KList<String>): Request
    //
    //        @Doc("List all meme templates.")
    //        @SerialName("ListAllMemes")
    //        @Serializable data object ListAllMemes: Request
    //
    //        @SerialName("GetJoke")
    //        @Doc("Get a random reddit joke.")
    //        @Serializable data object GetJoke : Request
    //
    //        @SerialName("SendMessage")
    //        @Serializable data class SendMessage(
    //            val replyTo: KLong? = null,
    //            val comprehensiveUnderstanding: String?,
    //            val internalReasoning: KList<String>?,
    //            val message: String,
    //            val metacognitiveSelfCritique: String?,
    //            val attachments: KList<String>,
    //            val continueConversation: KBoolean? = null
    //        ) : Request
    //
    //        @SerialName("RecordThought")
    //        @Serializable data class RecordThought(
    //            val thought: String
    //        ) : Request
    //
    //        @Serializable enum class FeaturePriority {
    //            Low, Medium, High, Critical
    //        }
    //
    //        @Serializable enum class FeatureUrgency {
    //            Low, Medium, High, Immediate
    //        }
    //
    //        @Serializable enum class ImpactScore {
    //            Minimal, Moderate, Significant, Transformative
    //        }
    //
    //        @Serializable enum class BugSeverity {
    //            Low, Medium, High, Critical
    //        }
    //
    //        @SerialName("RequestBug")
    //        @Serializable data class RequestBug(
    //            val bugDescription: String,
    //            val stepsToReproduce: KList<String>,
    //            val affectedFunctionality: String,
    //            val severity: BugSeverity,
    //            val impact: String,
    //            val relatedTools: KList<String>,
    //            val proposedSolution: String
    //        ) : Request
    //
    //        @SerialName("RequestFeature")
    //        @Serializable data class RequestFeature(
    //            val feature: String,
    //            val useCaseScenarios: KList<String>,
    //            val expectedImpact: String,
    //            val userFeedback: String,
    //            val integrationPoints: KList<String>,
    //            val estimatedImpactScore: ImpactScore,
    //            val priority: FeaturePriority,
    //            val urgency: FeatureUrgency,
    //            val metricsForSuccess: KList<String>
    //        ) : Request
    //
    //        @Doc("Create or modify a memory.")
    //        @SerialName("CreateOrModifyMemory")
    //        @Serializable data class CreateOrModifyMemory(
    //            @Doc("The title of the memory.")
    //            val title: String,
    //            @Doc("The content of the memory. Provide enough context to the memory so that you
    // can always understand why you made it.")
    //            val content: String?,
    //            @Doc("Whether the memory is important.")
    //            val important: Boolean?): Request
    //
    //        @SerialName("ShowNote")
    //        @Serializable data class ShowNote(
    //            val title: String): Request
    //
    //        @Doc("List all memories.")
    //        @SerialName("ListAllMemories")
    //        @Serializable data object ListAllMemories: Request
    //
    //        @Doc("Delete a memory.")
    //        @SerialName("DeleteMemory")
    //        @Serializable data class DeleteMemory(
    //            @Doc("The title of the memory.")
    //            val title: String): Request
    //
    //        @SerialName("GetAttachment")
    //        @Serializable data class GetAttachment(
    //            val url: String) : Request
    //    }
    // override val functions = listOf(
    //        ChatTool(
    //            type = ToolType.Function,
    //            function = FunctionTool(
    //                name = "get_current_weather",
    //                description = "Get the current weather in a location.",
    //                parameters = Parameters(Json.decodeFromString(
    //                    """
    //                        {
    //                            "type": "object",
    //                            "properties": {
    //                                "longitude": {
    //                                    "type": "number",
    //                                    "description": "The longitude of the location."
    //                                },
    //                                "latitude": {
    //                                    "type": "number",
    //                                    "description": "The latitude of the location."
    //                                }
    //                            },
    //                            "required": ["longitude", "latitude"]
    //                        }
    //                    """.trimIndent()))
    //            )
    //        )
    //    )

    @Serializable
    sealed interface Request {
        @Doc("Generate a meme given a template and text.")
        @SerialName("GenerateMeme")
        @Serializable
        data class GenerateMeme(
            @Doc("The name of the meme template on Imgflip.") val templateName: KString,
            @Doc("The text to put in each box of the meme.") val boxText: KList<String>,
        ) : Request

        @Doc("List all meme templates.")
        @SerialName("ListAllMemes")
        @Serializable
        data object ListAllMemes : Request
    }

    @Test
    fun `test`() {
        //        val req = FunctionSchema.parseRequest(
        //            Request.serializer(),
        //            Tool.RawRequest(
        //            "GetCurrentWeather", JsonObject(mapOf(
        //                "longitude" to JsonPrimitive(0.0),
        //                "latitude" to JsonPrimitive(0.0)
        //            )))
        //        )
        //
        //        println(req)

        val tools = FunctionSchema.makeFunctions(Request.serializer().descriptor)

        for (tool in tools) {
            println(tool)
        }
    }
}
