package com.smartatm.pretest.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.smartatm.pretest.mockcall.CallRole
import com.smartatm.pretest.mockcall.CallStatus
import com.smartatm.pretest.ui.MainViewModel
import com.smartatm.pretest.ui.theme.RiskCautionBg
import com.smartatm.pretest.ui.theme.RiskCautionYellow
import com.smartatm.pretest.ui.theme.RiskDangerBg
import com.smartatm.pretest.ui.theme.RiskDangerRed
import com.smartatm.pretest.ui.theme.RiskSafeBg
import com.smartatm.pretest.ui.theme.RiskSafeGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MockCallScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToResult: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.mockCallUiState.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val partialSttText by viewModel.partialSttText.collectAsState()
    val rmsVolume by viewModel.rmsVolume.collectAsState()
    val speechStatusText by viewModel.speechStatusText.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val authToken by viewModel.authToken.collectAsState()
    var targetIpInput by remember { mutableStateOf("") }

    // 마이크 권한 허용 후 이어서 실행할 동작 (발신 / 수락 / 통화 중 STT 시작)
    var pendingPermissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val action = pendingPermissionAction
        pendingPermissionAction = null
        if (isGranted) {
            action?.invoke()
        }
    }
    val runWithMicPermission: (() -> Unit) -> Unit = { action ->
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            action()
        } else {
            pendingPermissionAction = action
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // 시스템 표준 구글 음성인식 팝업 다이얼로그 런처 (하드웨어/기기 호환 100% 안전장치)
    val speechDialogLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
            if (!spokenText.isNullOrBlank()) {
                viewModel.handleVoiceRecognitionResult(spokenText)
            }
        }
    }

    val onLaunchSpeechDialog = {
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "음성으로 말씀하세요...")
        }
        try {
            speechDialogLauncher.launch(intent)
        } catch (e: Exception) {
            // Ignore
        }
    }

    // 통화 연결 시 음성 인식(STT) 자동 활성화 및 종료 시 자동 비활성화
    // 공격자 모드는 통화만 수행: STT를 켜지 않아 마이크가 통화 음성 송신에만 쓰임
    LaunchedEffect(uiState.callStatus, uiState.currentRole) {
        if (uiState.callStatus == CallStatus.IN_CALL && uiState.currentRole == CallRole.VICTIM) {
            runWithMicPermission {
                if (!viewModel.isListening.value) {
                    viewModel.startListening()
                }
            }
        } else if (uiState.callStatus != CallStatus.IN_CALL || uiState.currentRole == CallRole.ATTACKER) {
            if (isListening) {
                viewModel.stopListening()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Wi-Fi Mock 통화 시뮬레이션", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (uiState.callStatus) {
                CallStatus.IN_CALL -> {
                    InCallView(
                        uiState = uiState,
                        isListening = isListening,
                        partialSttText = partialSttText,
                        rmsVolume = rmsVolume,
                        speechStatusText = speechStatusText,
                        errorMessage = errorMessage,
                        onClearError = { viewModel.clearError() },
                        hasAuthToken = authToken.isNotBlank(),
                        onToggleListening = { viewModel.toggleListening() },
                        onLaunchSpeechDialog = onLaunchSpeechDialog,
                        onEndCall = { viewModel.mockCallManager.endCall() },
                        onToggleMute = { viewModel.mockCallManager.toggleMute() },
                        onToggleSpeaker = { viewModel.mockCallManager.toggleSpeaker() },
                        onSendScript = { script ->
                            viewModel.mockCallManager.sendTranscript(script, isLocalSender = true)
                        }
                    )
                }

                CallStatus.INCOMING_CALL -> {
                    IncomingCallView(
                        uiState = uiState,
                        onAccept = {
                            runWithMicPermission { viewModel.mockCallManager.acceptCall() }
                        },
                        onReject = { viewModel.mockCallManager.rejectCall() }
                    )
                }

                CallStatus.CALLING -> {
                    CallingOutView(
                        uiState = uiState,
                        onCancel = { viewModel.mockCallManager.rejectCall() }
                    )
                }

                CallStatus.ENDED -> {
                    CallEndedView(
                        uiState = uiState,
                        onConfirmResult = {
                            viewModel.confirmMockCallResult(onSuccess = onNavigateToResult)
                        },
                        onReset = {
                            viewModel.mockCallManager.resetTranscript()
                            viewModel.mockCallManager.resetConnection()
                        }
                    )
                }

                else -> {
                    // IDLE, WAITING_PEER, PEER_CONNECTED
                    LobbyView(
                        uiState = uiState,
                        targetIpInput = targetIpInput,
                        onTargetIpChange = { targetIpInput = it },
                        onRoleChange = { viewModel.mockCallManager.setRole(it) },
                        onCreateRoom = { viewModel.mockCallManager.createRoom() },
                        onJoinRoom = { ip -> viewModel.mockCallManager.joinRoom(ip) },
                        onStartCall = {
                            runWithMicPermission { viewModel.mockCallManager.makeCall() }
                        },
                        onReset = { viewModel.mockCallManager.resetConnection() }
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 1. 통화 중 메인 화면 (IN_CALL)
// -------------------------------------------------------------
@Composable
private fun InCallView(
    uiState: com.smartatm.pretest.mockcall.MockCallUiState,
    isListening: Boolean,
    partialSttText: String,
    rmsVolume: Float,
    speechStatusText: String,
    errorMessage: String?,
    onClearError: () -> Unit,
    hasAuthToken: Boolean,
    onToggleListening: () -> Unit,
    onLaunchSpeechDialog: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onSendScript: (String) -> Unit
) {
    val listState = rememberLazyListState()
    // 공격자 모드: 통화 전용 (STT/위험도 분석 UI 숨김)
    val isVictim = uiState.currentRole == CallRole.VICTIM

    // 새 자막이 들어올 때마다 자동 스크롤
    LaunchedEffect(uiState.liveTranscript.size) {
        if (uiState.liveTranscript.isNotEmpty()) {
            listState.animateScrollToItem(uiState.liveTranscript.size - 1)
        }
    }

    val riskBgColor by animateColorAsState(
        targetValue = when (uiState.realTimeRiskLevel) {
            "DANGER" -> RiskDangerBg
            "CAUTION" -> RiskCautionBg
            else -> RiskSafeBg
        },
        label = "riskBg"
    )

    val riskTextColor = when (uiState.realTimeRiskLevel) {
        "DANGER" -> RiskDangerRed
        "CAUTION" -> RiskCautionYellow
        else -> RiskSafeGreen
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 음성인식/네트워크 에러 알림 카드
        if (isVictim && !errorMessage.isNullOrBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFC62828),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage,
                            color = Color(0xFFC62828),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = onClearError,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Text("✕", fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 상단 통화 정보 헤더
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PhoneInTalk,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "통화 중: ${uiState.remotePeerIp ?: "상대방"}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "내 역할: ${uiState.currentRole.title}",
                                fontSize = 12.sp,
                                color = Color.DarkGray
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                // 통화 중 자막 판정은 늘 앱에서 한다(서버와 같은 규칙). 서버는 통화 종료 후 한 번
                                text = when {
                                    !isVictim -> "• 통화 전용 모드"
                                    hasAuthToken -> "• 종료 후 서버 확인 · QR 발급"
                                    else -> "• 앱 판정만 (QR은 로그인 필요)"
                                },
                                fontSize = 11.sp,
                                color = when {
                                    !isVictim -> Color.Gray
                                    hasAuthToken -> Color(0xFF1565C0)
                                    else -> Color(0xFF2E7D32)
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 타이머
                val minutes = uiState.callDurationSeconds / 60
                val seconds = uiState.callDurationSeconds % 60
                Text(
                    text = String.format("%02d:%02d", minutes, seconds),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF1E88E5)
                )
            }
        }

        // 실시간 위험도 분석 카드 (피해자 화면의 핵심!)
        if (isVictim) Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = riskBgColor),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (uiState.realTimeRiskLevel) {
                                "DANGER" -> Icons.Default.Warning
                                "CAUTION" -> Icons.Default.Warning
                                else -> Icons.Default.CheckCircle
                            },
                            contentDescription = null,
                            tint = riskTextColor
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "실시간 위험 등급: ${uiState.realTimeRiskLevel}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = riskTextColor
                        )
                    }

                    Text(
                        text = "${uiState.realTimeRiskScore} / 100점",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = riskTextColor
                    )
                }

                // 위험 경고 배너
                AnimatedVisibility(visible = !uiState.warningMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFFCDD2))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = uiState.warningMessage ?: "",
                            color = Color(0xFFB71C1C),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // 실시간 통화 자막 창 (STT Transcript)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isVictim) "실시간 음성 자막 (STT 누적 대화):" else "통화 자막 (전송한 대본 / 상대방 자막):",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (isVictim) Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isListening) Color(0xFF2E7D32) else Color.Gray)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isListening) speechStatusText else "인식 일시정지",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isListening) Color(0xFF2E7D32) else Color.Gray,
                            maxLines = 1
                        )
                        if (isListening) {
                            Spacer(modifier = Modifier.width(6.dp))
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { rmsVolume },
                                modifier = Modifier
                                    .width(48.dp)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = Color(0xFF2E7D32),
                                trackColor = Color(0xFFC8E6C9)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        // 시스템 구글 음성인식 팝업 버튼 (호환성 100% 백업)
                        Surface(
                            onClick = onLaunchSpeechDialog,
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFE3F2FD),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🎙️구글 팝업", fontSize = 10.sp, color = Color(0xFF1565C0), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                // 실시간 음성인식 임시 프리뷰 (사용자가 말하고 있을 때 즉각 반응 표시)
                if (isListening && partialSttText.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFE8F5E9))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = partialSttText,
                            color = Color(0xFF2E7D32),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (uiState.liveTranscript.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (!isVictim) {
                                "📞 공격자 모드: 음성 통화만 진행됩니다.\n(음성 인식·보이스피싱 분석은 피해자 기기에서만 동작)\n소음이 많을 때는 아래 대본 칩으로 자막을 보낼 수 있습니다."
                            } else if (isListening) {
                                "🎙️ 마이크로 말씀하시면 실시간 텍스트로 변환 및 분석됩니다.\n소음이 많거나 테스트 시 아래 대본 칩을 전송할 수 있습니다."
                            } else {
                                "음성 인식이 일시정지되었습니다.\n하단 귀 모양/마이크 버튼을 눌러 다시 활성화하세요."
                            },
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.liveTranscript) { item ->
                            val isAttacker = item.speakerRole == CallRole.ATTACKER
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isAttacker) Arrangement.Start else Arrangement.End
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.85f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isAttacker) Color(0xFFFFEBEE) else Color(0xFFE8F5E9))
                                        .border(
                                            1.dp,
                                            if (isAttacker) Color(0xFFFFCDD2) else Color(0xFFC8E6C9),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = if (isAttacker) "🚨 상대방 (공격자)" else "🛡️ 피해자",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isAttacker) Color(0xFFC62828) else Color(0xFF2E7D32)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = item.text,
                                            fontSize = 15.sp,
                                            color = Color.Black
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 시연용 퀵 대본 전송 버튼 (전시장/주변 소음 및 단독 테스트 대비용)
        Column(modifier = Modifier.fillMaxWidth()) {
            if (uiState.currentRole == CallRole.ATTACKER) {
                Text(
                    text = "공격자 시연 대본 퀵 전송 (원클릭):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = false,
                        onClick = { onSendScript("여보세요, 서울중앙지검 수사관입니다. 대포통장 사건에 연루되어 수사 중입니다.") },
                        label = { Text("1. 수사관 사칭 (주의)", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { onSendScript("지금 즉시 은행에 가셔서 예금을 전액 인출하여 안전계좌로 입금하셔야 합니다.") },
                        label = { Text("2. 안전계좌 이체 (위험 100점)", fontSize = 11.sp) }
                    )
                }
            } else {
                Text(
                    text = "피해자 시연 응답 퀵 전송 (원클릭):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = false,
                        onClick = { onSendScript("누구시라고요? 제 통장이 왜 범죄에 연루되었나요?") },
                        label = { Text("1. 의심 질문", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { onSendScript("돈을 찾아서 어디로 가져가면 되나요? 알려주세요.") },
                        label = { Text("2. 인출 문의", fontSize = 11.sp) }
                    )
                }
            }
        }

        // 하단 통화 컨트롤 버튼 바
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 마이크 음소거
            IconButton(
                onClick = onToggleMute,
                modifier = Modifier
                    .size(54.dp)
                    .background(
                        if (uiState.isMuted) Color(0xFFFFCDD2) else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = if (uiState.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "음소거",
                    tint = if (uiState.isMuted) Color.Red else Color.Black
                )
            }

            // 스피커폰
            IconButton(
                onClick = onToggleSpeaker,
                modifier = Modifier
                    .size(54.dp)
                    .background(
                        if (uiState.isSpeakerOn) Color(0xFFC8E6C9) else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "스피커폰",
                    tint = if (uiState.isSpeakerOn) Color(0xFF2E7D32) else Color.Black
                )
            }

            // 음성인식(STT) 토글 (피해자 전용)
            if (isVictim) IconButton(
                onClick = onToggleListening,
                modifier = Modifier
                    .size(54.dp)
                    .background(
                        if (isListening) Color(0xFFE1BEE7) else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.PhoneInTalk else Icons.Default.Call,
                    contentDescription = "실시간 STT 음성인식 토글",
                    tint = if (isListening) Color(0xFF7B1FA2) else Color.Gray
                )
            }

            // 빨간색 큰 통화 종료 버튼
            Button(
                onClick = onEndCall,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                shape = CircleShape,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "통화 종료",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 2. 수신 중 화면 (INCOMING_CALL)
// -------------------------------------------------------------
@Composable
private fun IncomingCallView(
    uiState: com.smartatm.pretest.mockcall.MockCallUiState,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.PhoneInTalk,
            contentDescription = null,
            tint = Color(0xFF1E88E5),
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "전화가 걸려왔습니다",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "발신자: ${uiState.remotePeerIp ?: "공격자"}",
            fontSize = 16.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(48.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = onReject,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                shape = CircleShape,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = "거절", tint = Color.White)
            }

            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                shape = CircleShape,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(Icons.Default.Call, contentDescription = "수락", tint = Color.White)
            }
        }
    }
}

// -------------------------------------------------------------
// 3. 발신 중 화면 (CALLING)
// -------------------------------------------------------------
@Composable
private fun CallingOutView(
    uiState: com.smartatm.pretest.mockcall.MockCallUiState,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "전화 연결 중...",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "상대방(${uiState.remotePeerIp})의 응답을 기다리고 있습니다.",
            fontSize = 15.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onCancel,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
        ) {
            Text("발신 취소")
        }
    }
}

// -------------------------------------------------------------
// 4. 통화 종료 후 결과 확인 화면 (ENDED)
// -------------------------------------------------------------
@Composable
private fun CallEndedView(
    uiState: com.smartatm.pretest.mockcall.MockCallUiState,
    onConfirmResult: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "통화가 종료되었습니다",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "통화 시간: ${uiState.callDurationSeconds}초",
            fontSize = 15.sp,
            color = Color.DarkGray
        )

        if (uiState.currentRole == CallRole.ATTACKER) {
            Text(
                text = "공격자 모드에서는 보이스피싱 위험도 분석을 하지 않습니다.\n분석 결과와 ATM 차단 QR은 피해자 기기에서 확인하세요.",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        // 최종 위험도 판정 카드 (피해자 전용)
        if (uiState.currentRole == CallRole.VICTIM) Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when (uiState.realTimeRiskLevel) {
                    "DANGER" -> RiskDangerBg
                    "CAUTION" -> RiskCautionBg
                    else -> RiskSafeBg
                }
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "최종 통화 위험도: ${uiState.realTimeRiskLevel}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (uiState.realTimeRiskLevel) {
                        "DANGER" -> RiskDangerRed
                        "CAUTION" -> RiskCautionYellow
                        else -> RiskSafeGreen
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "위험 점수: ${uiState.realTimeRiskScore}점 / 100점",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                if (uiState.realTimeReasons.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "탐지 근거:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    uiState.realTimeReasons.forEach { reason ->
                        Text(text = "• $reason", fontSize = 14.sp)
                    }
                }
            }
        }

        // 위험일 경우 ATM 출금 차단 QR 생성 버튼 (피해자 전용)
        if (uiState.currentRole == CallRole.VICTIM && uiState.realTimeRiskLevel != "SAFE") {
            Button(
                onClick = onConfirmResult,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.QrCode, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "라즈베리파이 ATM 출금 차단 QR 보기 (S9)",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        OutlinedButton(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("새로운 Mock 통화 준비")
        }
    }
}

// -------------------------------------------------------------
// 5. 로비 / 방 연결 설정 화면 (LobbyView)
// -------------------------------------------------------------
@Composable
private fun LobbyView(
    uiState: com.smartatm.pretest.mockcall.MockCallUiState,
    targetIpInput: String,
    onTargetIpChange: (String) -> Unit,
    onRoleChange: (CallRole) -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: (String) -> Unit,
    onStartCall: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 역할 선택
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "1. 내 역할 선택",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = uiState.currentRole == CallRole.ATTACKER,
                        onClick = { onRoleChange(CallRole.ATTACKER) },
                        label = { Text("🚨 공격자 (보이스피싱)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = uiState.currentRole == CallRole.VICTIM,
                        onClick = { onRoleChange(CallRole.VICTIM) },
                        label = { Text("🛡️ 피해자 (어르신)") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 내 IP 및 통화 세션 연결
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "2. Wi-Fi 통화 세션 연결",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "내 IP 주소: ${uiState.localIp}",
                    color = Color(0xFF1E88E5),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                when (uiState.callStatus) {
                    CallStatus.WAITING_PEER -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("상대방 접속 대기 중... (비콘 송출)", fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = onReset) { Text("취소") }
                        }
                    }

                    CallStatus.PEER_CONNECTED -> {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("상대방 기기 연결됨 (${uiState.remotePeerIp})", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = onStartCall,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            ) {
                                Icon(Icons.Default.Call, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("전화 걸기 (CALL OFFER)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    else -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onCreateRoom,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("방 만들기 (호스트)")
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(text = "또는 상대방 IP로 직접 참여:", fontSize = 13.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = targetIpInput,
                                onValueChange = onTargetIpChange,
                                placeholder = { Text("상대방 IP (예: 192.168.0.x)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Button(
                                onClick = { onJoinRoom(targetIpInput.trim()) },
                                enabled = targetIpInput.isNotBlank()
                            ) {
                                Text("참여")
                            }
                        }
                    }
                }
            }
        }

        // Wi-Fi 자동 검색된 방 목록 (Beacon Discovery)
        if (uiState.discoveredRooms.isNotEmpty() && uiState.callStatus == CallStatus.IDLE) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8EAF6))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "📡 같은 Wi-Fi에서 탐색된 통화 방:",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF283593)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    uiState.discoveredRooms.forEach { room ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "방: ${room.roomId} (${room.hostIp})", fontWeight = FontWeight.SemiBold)
                                Text(text = "호스트: ${room.hostRole.name}", fontSize = 12.sp, color = Color.Gray)
                            }
                            Button(onClick = { onJoinRoom(room.hostIp) }) {
                                Text("원클릭 참여")
                            }
                        }
                    }
                }
            }
        }
    }
}
