package com.smartatm.pretest.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartatm.pretest.data.model.SampleMessages
import com.smartatm.pretest.data.model.SmsMessage
import com.smartatm.pretest.ui.MainViewModel
import com.smartatm.pretest.ui.theme.RiskCautionBg
import com.smartatm.pretest.ui.theme.RiskCautionYellow
import com.smartatm.pretest.ui.theme.RiskDangerBg
import com.smartatm.pretest.ui.theme.RiskDangerRed
import com.smartatm.pretest.ui.theme.RiskSafeBg
import com.smartatm.pretest.ui.theme.RiskSafeGreen

// 갤럭시(One UI) 메시지 앱과 유사한 색상
private val SmsBackground = Color(0xFFFCFCFC)
private val SmsTextPrimary = Color(0xFF1A1A1A)
private val SmsTextSecondary = Color(0xFF7A7A7A)
private val SmsUnreadBadge = Color(0xFFF26B1D)
private val SmsIncomingBubble = Color(0xFFEFEFF1)
private val SmsAccent = Color(0xFF3E91FF)
private val AvatarColors = listOf(
    Color(0xFF7EA6F4), Color(0xFFF49B7E), Color(0xFF8BCB8B), Color(0xFFC59BE8), Color(0xFFF2C46B)
)

// -------------------------------------------------------------
// 1. 문자 목록 화면 (갤럭시 메시지 앱 대화 목록 스타일)
// -------------------------------------------------------------
@Composable
fun SmsListScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onOpenMessage: (Int) -> Unit,
    onNavigateToManualInput: () -> Unit
) {
    val checkResults by viewModel.smsCheckResults.collectAsState()
    val messages = SampleMessages.all
    val unreadTotal = messages.count { it.unreadCount > 0 && it.id !in checkResults }

    Scaffold(
        containerColor = SmsBackground,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToManualInput,
                containerColor = SmsAccent,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                text = { Text("직접 입력", fontWeight = FontWeight.Bold) }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // One UI 스타일 큰 제목 영역
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기", tint = SmsTextPrimary)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = SmsTextSecondary,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 36.dp, bottom = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("메시지", fontSize = 34.sp, fontWeight = FontWeight.Normal, color = SmsTextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (unreadTotal > 0) "읽지 않은 메시지 ${unreadTotal}개" else "문자를 눌러 보이스피싱 여부를 검사하세요",
                        fontSize = 14.sp,
                        color = if (unreadTotal > 0) SmsUnreadBadge else SmsTextSecondary
                    )
                }
            }

            items(messages, key = { it.id }) { msg ->
                SmsListItem(
                    message = msg,
                    checkedLevel = checkResults[msg.id],
                    onClick = { onOpenMessage(msg.id) }
                )
            }

            item { Spacer(modifier = Modifier.height(96.dp)) } // FAB 가림 방지
        }
    }
}

@Composable
private fun SmsListItem(
    message: SmsMessage,
    checkedLevel: String?,
    onClick: () -> Unit
) {
    val isUnread = message.unreadCount > 0 && checkedLevel == null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SenderAvatar(message = message, size = 50)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message.sender,
                    fontSize = 17.sp,
                    fontWeight = if (isUnread) FontWeight.Bold else FontWeight.SemiBold,
                    color = SmsTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (checkedLevel != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    RiskLevelChip(checkedLevel)
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = message.body,
                fontSize = 14.sp,
                color = if (isUnread) SmsTextPrimary else SmsTextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 19.sp
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(text = message.timeLabel, fontSize = 12.sp, color = SmsTextSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            if (isUnread) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(SmsUnreadBadge, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${message.unreadCount}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun SenderAvatar(message: SmsMessage, size: Int) {
    if (message.isContact) {
        Box(
            modifier = Modifier
                .size(size.dp)
                .background(AvatarColors[message.id % AvatarColors.size], CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message.sender.take(1),
                color = Color.White,
                fontSize = (size * 0.4).sp,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        // 저장되지 않은 번호: 회색 사람 아이콘
        Box(
            modifier = Modifier
                .size(size.dp)
                .background(Color(0xFFDADCE0), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size((size * 0.6).dp)
            )
        }
    }
}

@Composable
private fun RiskLevelChip(level: String) {
    val (label, fg, bg) = when (level) {
        "DANGER" -> Triple("위험", RiskDangerRed, RiskDangerBg)
        "CAUTION" -> Triple("주의", RiskCautionYellow, RiskCautionBg)
        else -> Triple("안전", RiskSafeGreen, RiskSafeBg)
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(label, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// -------------------------------------------------------------
// 2. 문자 대화 화면 (말풍선 + 보이스피싱 검사 버튼)
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsDetailScreen(
    viewModel: MainViewModel,
    messageId: Int,
    onNavigateBack: () -> Unit,
    onNavigateToResult: () -> Unit
) {
    val message = SampleMessages.findById(messageId)
    val isLoading by viewModel.isLoading.collectAsState()
    val checkResults by viewModel.smsCheckResults.collectAsState()

    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = message?.sender ?: "메시지",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (message != null && message.isContact) {
                            Text(text = message.phoneNumber, fontSize = 12.sp, color = SmsTextSecondary)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            if (message != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .navigationBarsPadding()
                ) {
                    HorizontalDivider(color = Color(0xFFEAEAEA))
                    Button(
                        onClick = {
                            viewModel.analyzeSms(message.id, message.body, onSuccess = onNavigateToResult)
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SmsAccent)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("검사 중...", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text(
                                text = if (checkResults.containsKey(message.id)) "🔍 다시 검사하기" else "🔍 보이스피싱 검사하기",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        if (message == null) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("메시지를 찾을 수 없습니다.", color = SmsTextSecondary)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (!message.isContact) {
                // 갤럭시처럼 저장되지 않은 번호 안내
                Text(
                    text = "연락처에 저장되지 않은 번호입니다. 링크나 송금 요구에 주의하세요.",
                    fontSize = 12.sp,
                    color = SmsTextSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5F5F7), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 날짜 구분선
            Text(
                text = message.dateLabel,
                fontSize = 12.sp,
                color = SmsTextSecondary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 10.dp)
            )

            // 받은 메시지 말풍선
            Row(verticalAlignment = Alignment.Top) {
                SenderAvatar(message = message, size = 36)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = message.sender, fontSize = 12.sp, color = SmsTextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = highlightLinks(message.body),
                            fontSize = 16.sp,
                            lineHeight = 23.sp,
                            color = SmsTextPrimary,
                            modifier = Modifier
                                .widthIn(max = 260.dp)
                                .background(SmsIncomingBubble, RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = message.timeLabel, fontSize = 11.sp, color = SmsTextSecondary)
                    }
                }
            }

            checkResults[message.id]?.let { level ->
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("최근 검사 결과 ", fontSize = 13.sp, color = SmsTextSecondary)
                    RiskLevelChip(level)
                }
            }
        }
    }
}

/** 본문 속 URL을 실제 문자 앱처럼 파란 밑줄로 표시 (터치해도 열리지 않음) */
private fun highlightLinks(text: String) = buildAnnotatedString {
    val urlRegex = Regex("(https?://|www\\.)\\S+")
    var last = 0
    for (match in urlRegex.findAll(text)) {
        append(text.substring(last, match.range.first))
        withStyle(SpanStyle(color = SmsAccent, textDecoration = TextDecoration.Underline)) {
            append(match.value)
        }
        last = match.range.last + 1
    }
    append(text.substring(last))
}
