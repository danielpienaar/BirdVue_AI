package com.varsitycollege.birdvue.api // Or your package for API data classes

import com.google.gson.annotations.SerializedName

// For the main request body to Lex
data class LexV2BotRequest(
    @SerializedName("text")
    val text: String,

    @SerializedName("sessionState")
    val sessionState: LexSessionStateInput,

    // Optional: Include if you need to pass specific request attributes
    // @SerializedName("requestAttributes")
    // val requestAttributes: Map<String, String>? = null,

    // Optional: You might need to manage sessionId client-side or let Lex handle it
    @SerializedName("sessionId")
    val sessionId: String
)

// For the sessionState part of the request
data class LexSessionStateInput(
    @SerializedName("sessionAttributes")
    val sessionAttributes: Map<String, String>? = null, // e.g., "species", "summary"

    // Optional: If you need to manage dialogAction from the client
    // @SerializedName("dialogAction")
    // val dialogAction: LexDialogActionInput? = null,

    // Optional: If you need to manage intent state from the client
    // @SerializedName("intent")
    // val intent: LexIntentInput? = null
)

// Main response structure from Lex
data class LexV2BotResponse(
    @SerializedName("messages")
    val messages: List<LexMessage>?, // List of messages from Lex

    @SerializedName("sessionState")
    val sessionState: LexSessionStateOutput?,

    @SerializedName("interpretations")
    val interpretations: List<LexInterpretation>?,

    @SerializedName("requestAttributes")
    val requestAttributes: Map<String, String>?,

    @SerializedName("sessionId")
    val sessionId: String?
)

// For each message in the response
data class LexMessage(
    @SerializedName("content")
    val content: String?,

    @SerializedName("contentType")
    val contentType: String? // e.g., "PlainText", "SSML", "CustomPayload"
    // You might add imageResponseCard if you use response cards
)

// For the sessionState part of the response
data class LexSessionStateOutput(
    @SerializedName("dialogAction")
    val dialogAction: LexDialogActionOutput?,

    @SerializedName("intent")
    val intent: LexIntentOutput?,

    @SerializedName("sessionAttributes")
    val sessionAttributes: Map<String, String>?,

    @SerializedName("originatingRequestId")
    val originatingRequestId: String?
)

// For the dialogAction in the response
data class LexDialogActionOutput(
    @SerializedName("type")
    val type: String? // e.g., "Close", "ElicitIntent", "ElicitSlot"
    // ... other fields like slotToElicit
)

// For the intent in the response and interpretations
data class LexIntentOutput(
    @SerializedName("name")
    val name: String?,

    @SerializedName("slots")
    val slots: Map<String, LexSlotOutput?>?, // Slots can be complex, represented as a map

    @SerializedName("state")
    val state: String?, // e.g., "Fulfilled", "InProgress", "Failed"

    @SerializedName("confirmationState")
    val confirmationState: String? // e.g., "None", "Confirmed", "Denied"
)

// For each slot in the intent
data class LexSlotOutput(
    @SerializedName("value")
    val value: LexSlotValueOutput?,
    @SerializedName("shape")
    val shape: String?,
    @SerializedName("values")
    val values: List<LexSlotOutput>? // For multi-value slots
)

data class LexSlotValueOutput(
    @SerializedName("originalValue")
    val originalValue: String?,
    @SerializedName("interpretedValue")
    val interpretedValue: String?,
    @SerializedName("resolvedValues")
    val resolvedValues: List<String>?
)


// For interpretations
data class LexInterpretation(
    @SerializedName("nluConfidence")
    val nluConfidence: LexNluConfidence?,

    @SerializedName("intent")
    val intent: LexIntentOutput?,

    @SerializedName("interpretationSource")
    val interpretationSource: String? // e.g., "Lex", "Kendra"
)

data class LexNluConfidence(
    @SerializedName("score")
    val score: Double?
)

// --- Optional Input Sub-Classes (if needed for more complex scenarios) ---
// data class LexDialogActionInput(
//    @SerializedName("type")
//    val type: String // e.g., "ElicitSlot", "ConfirmIntent", "Close"
//    // ... other dialogAction fields if needed
// )

// data class LexIntentInput(
//    @SerializedName("name")
//    val name: String,
//    @SerializedName("slots")
//    val slots: Map<String, LexSlotInput?>? = null
//    // ... other intent fields if needed
// )

// data class LexSlotInput(
//    @SerializedName("value")
//    val value: LexSlotValueInput?
// )

// data class LexSlotValueInput(
//    @SerializedName("interpretedValue")
//    val interpretedValue: String
// )
