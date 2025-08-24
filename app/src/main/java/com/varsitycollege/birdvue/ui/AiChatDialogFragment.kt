package com.varsitycollege.birdvue.ui

import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.varsitycollege.birdvue.R
import com.varsitycollege.birdvue.api.LexV2BotRequest
import com.varsitycollege.birdvue.api.LexSessionStateInput
import com.varsitycollege.birdvue.api.BirdInfoAPI
import com.varsitycollege.birdvue.data.ChatMessage
import com.varsitycollege.birdvue.data.Observation
import com.varsitycollege.birdvue.data.SenderType
import com.varsitycollege.birdvue.databinding.DialogAiChatBinding // Your existing dialog layout binding
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.UUID
import java.util.concurrent.TimeUnit

// Import your Retrofit instance creator (e.g., RetrofitInstance.api)

class AiChatAdapter(private val messages: List<ChatMessage>) : RecyclerView.Adapter<AiChatAdapter.MessageViewHolder>() {

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_AI = 2
    }

    abstract class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        abstract fun bind(message: ChatMessage)
    }

    class UserMessageViewHolder(itemView: View) : MessageViewHolder(itemView) {
        private val messageTextView: android.widget.TextView = itemView.findViewById(R.id.chatMessageTextView)
        override fun bind(message: ChatMessage) {
            messageTextView.text = message.text
        }
    }

    class AiMessageViewHolder(itemView: View) : MessageViewHolder(itemView) {
        private val messageTextView: android.widget.TextView = itemView.findViewById(R.id.chatMessageTextView)
        override fun bind(message: ChatMessage) {
            messageTextView.text = message.text
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].sender == SenderType.USER) VIEW_TYPE_USER else VIEW_TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_USER) {
            UserMessageViewHolder(inflater.inflate(R.layout.item_chat_message_user, parent, false))
        } else {
            AiMessageViewHolder(inflater.inflate(R.layout.item_chat_message_ai, parent, false))
        }
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount() = messages.size
}


class AiChatDialogFragment : DialogFragment() {

    private var _binding: DialogAiChatBinding? = null
    private val binding get() = _binding!!

    private var observationData: Observation? = null
    private lateinit var chatAdapter: AiChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()

    // Current Lex session ID - should persist for the duration of the chat session in this dialog
    private var currentLexSessionId: String = UUID.randomUUID().toString()
    // Store current session attributes from Lex to send back
    private var currentSessionAttributes: Map<String, String>? = null
    // TODO: Define your Lex Bot Endpoint URL
    // This might be constructed using Bot ID, Alias, Locale from constants or config
    // Example: "https://runtime-v2-lex.us-east-1.amazonaws.com/bots/YOUR_BOT_ID/botAliases/YOUR_BOT_ALIAS_ID/botLocales/en_US/text"
    // OR your API Gateway URL that proxies to Lex
    private val LEX_ENDPOINT_URL = "https://kpcs4l6aa3.execute-api.eu-west-1.amazonaws.com/BirdRESTApiStage/lex/text/"


    // TODO: Initialize your BirdInfoAPI service (Retrofit)
    private val birdInfoApiService: BirdInfoAPI by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://kpcs4l6aa3.execute-api.eu-west-1.amazonaws.com/BirdRESTApiStage/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return@lazy retrofit.create(BirdInfoAPI::class.java)
    }

    companion object {
        const val TAG = "AiChatDialog"
        private const val ARG_OBSERVATION = "arg_observation"

        fun newInstance(observation: Observation): AiChatDialogFragment {
            val args = Bundle()
            args.putParcelable(ARG_OBSERVATION, observation)
            val fragment = AiChatDialogFragment()
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            observationData = it.getParcelable(ARG_OBSERVATION)
        }
        // Initialize currentLexSessionId here if you want it fresh per dialog instance
        currentLexSessionId = "birdvue-${UUID.randomUUID()}" // Add a prefix for easier identification
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAiChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        addInitialAiMessage()
    }

    private fun setupUI() {
        binding.dialogTitleTextView.text = "AI Chat: ${observationData?.birdName ?: "Observation"}"
        val contextSummary = "Species: ${observationData?.birdName ?: "N/A"}. Sighting: ${observationData?.details ?: "No caption."}"
        binding.observationContextTextView.text = contextSummary

        binding.closeButton.setOnClickListener { dismiss() }

        chatAdapter = AiChatAdapter(chatMessages) // Your adapter from previous response
        binding.chatMessagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }
            adapter = chatAdapter
        }

        binding.sendChatMessageButton.setOnClickListener {
            val messageText = binding.chatMessageEditText.text.toString().trim()
            if (messageText.isNotEmpty()) {
                addUserMessage(messageText)
                binding.chatMessageEditText.text.clear()
                sendMessageToLex(messageText)
            }
        }
    }

    private fun addInitialAiMessage() {
        val initialMessage = "Hello! Ask me about the ${observationData?.birdName ?: "sighting"}."
        chatMessages.add(ChatMessage(text = initialMessage, sender = SenderType.AI))
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        // Optionally, you could send an initial "WELCOME" event to Lex here to get its greeting
    }

    private fun addUserMessage(text: String) {
        chatMessages.add(ChatMessage(text = text, sender = SenderType.USER))
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        binding.chatMessagesRecyclerView.scrollToPosition(chatMessages.size - 1)
    }

    private fun addAiResponseMessage(text: String) {
        chatMessages.add(ChatMessage(text = text, sender = SenderType.AI))
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        binding.chatMessagesRecyclerView.scrollToPosition(chatMessages.size - 1)
    }

    private fun sendMessageToLex(message: String) {
        binding.chatLoadingIndicator.visibility = View.VISIBLE
        binding.sendChatMessageButton.isEnabled = false

        // Prepare session attributes. Start with observation data, then merge with current from Lex.
        val initialSessionAttributes = mutableMapOf<String, String>()
        observationData?.birdName?.let { initialSessionAttributes["species"] = it }
        observationData?.details?.let { initialSessionAttributes["summary"] = it } // Or a more detailed summary
        // You can add more observation details here: location, date, etc.
        // observationData?.scientificName?.let { initialSessionAttributes["scientific_name"] = it }

        // Merge with attributes Lex might have returned and we stored
        val attributesToSend = currentSessionAttributes?.toMutableMap() ?: mutableMapOf()
        attributesToSend.putAll(initialSessionAttributes) // Initial observation data can override if needed, or vice-versa

        val lexRequest = LexV2BotRequest(
            text = message,
            sessionId = currentLexSessionId,
            sessionState = LexSessionStateInput(
                sessionAttributes = attributesToSend.ifEmpty { null } // Send null if empty, or Lex might complain
            )
        )

        lifecycleScope.launch {
            try {
                if (LEX_ENDPOINT_URL == "YOUR_API_GATEWAY_LEX_PROXY_URL_OR_LEX_ENDPOINT") {
                    addAiResponseMessage("AI Chatbot endpoint not configured.")
                    Log.e(TAG, "LEX_ENDPOINT_URL is not set.")
                    return@launch
                }

                val sessionId = "birdvue-${UUID.randomUUID()}"

                val response = birdInfoApiService.postTextToLex(
                    LEX_ENDPOINT_URL + sessionId,
                    lexRequest
                )

                if (response.isSuccessful && response.body() != null) {
                    val lexResponse = response.body()!!
                    lexResponse.messages?.forEach { lexMsg ->
                        if (lexMsg.contentType == "PlainText" && !lexMsg.content.isNullOrBlank()) {
                            addAiResponseMessage(lexMsg.content)
                        }
                        // Handle other content types if needed (e.g., ImageResponseCard)
                    }
                    // Update session attributes and potentially session ID from the response
                    currentSessionAttributes = lexResponse.sessionState?.sessionAttributes
                    lexResponse.sessionId?.let { currentLexSessionId = it } // Lex might manage/return session ID

                    if (lexResponse.messages.isNullOrEmpty()) {
                        addAiResponseMessage("I received a response, but it was empty.")
                        Log.w("AiChatDialog", "Lex response was empty: $lexResponse")
                    }

                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown API error"
                    addAiResponseMessage("Sorry, I couldn't get a response. Error: ${response.code()}")
                    Log.e(TAG, "Lex API Error: ${response.code()} - $errorBody")
                }
            } catch (e: HttpException) {
                addAiResponseMessage("Network error: ${e.message()}. Please check your connection.")
                Log.e(TAG, "Lex Network Error (HttpException)", e)
            } catch (e: Exception) {
                addAiResponseMessage("An unexpected error occurred: ${e.message}")
                Log.e(TAG, "Lex Unexpected Error", e)
            } finally {
                binding.chatLoadingIndicator.visibility = View.GONE
                binding.sendChatMessageButton.isEnabled = true
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            val displayMetrics = DisplayMetrics()
            activity?.windowManager?.defaultDisplay?.getMetrics(displayMetrics)
            val width = (displayMetrics.widthPixels * 0.95).toInt()
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
            window.setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
