package com.pasich.encly.presentation.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.composables.icons.lucide.BadgeHelp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.HandHelping
import com.composables.icons.lucide.Lucide
import com.pasich.encly.R
import com.pasich.encly.presentation.components.HeroCard
import com.pasich.encly.presentation.components.HeroIcon
import com.pasich.encly.presentation.components.TitleCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaqScreen(
    navController: NavHostController
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.about_faq_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Section
            HeroCard(
                icon = HeroIcon.Vector(Lucide.BadgeHelp),
                title = stringResource(R.string.about_faq_title),
                subtitle = "Відповіді на найпоширеніші запитання",
                description = "Нотатки, завдання, локальне шифрування та відновлення доступу без хмарної синхронізації."
            )

            // FAQ Content
            FaqContentSection()
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}


@Composable
private fun FaqContentSection() {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TitleCard("Основні функції", modifier = Modifier.padding(horizontal = 20.dp))

        FaqItem(
            question = "Як створити нову нотатку?",
            answer = "Натисніть кнопку '+' на головному екрані. У редакторі можна використовувати текст, заголовки, цитати, списки, розділювачі та посилання."
        )
        FaqItem(
            question = "Як зберігаються зміни?",
            answer = "Encly автоматично зберігає зміни локально. Кнопка «Готово» та вихід з редактора також завершують запис перед поверненням до списку."
        )
        FaqItem(
            question = "Як працюють мітки?",
            answer = "Мітки допомагають групувати нотатки. Їх можна створювати, перейменовувати, приховувати, видаляти та використовувати як фільтр на головному екрані."
        )
        FaqItem(
            question = "Що вміють завдання?",
            answer = "Завдання мають опис, пріоритет і локальну дату. Їх можна фільтрувати та відмічати виконаними. Encly не надсилає текст завдань у системні сповіщення."
        )

        TitleCard("Безпека та відновлення", modifier = Modifier.padding(horizontal = 20.dp))

        FaqItem(
            question = "Де зберігаються мої дані?",
            answer = "Нотатки, мітки та завдання зберігаються тільки в локальній SQLCipher-базі Encly. Поточна beta не має хмарної синхронізації або автоматичного резервного копіювання."
        )
        FaqItem(
            question = "Як захищений ключ бази даних?",
            answer = "Encly створює випадковий 256-бітний ключ бази. PIN захищає окремий AES-GCM unlock-slot, а біометрія використовує auth-per-use ключ Android Keystore."
        )
        FaqItem(
            question = "Для чого recovery seed?",
            answer = "Recovery seed створюється лише в user-managed режимі та є окремим способом відновити ключ vault. Зберігайте його офлайн. У звичайному режимі розблокування використовується PIN або біометрія."
        )
        FaqItem(
            question = "Що буде, якщо я забуду PIN?",
            answer = "Якщо ви створили recovery seed, на екрані блокування можна відновити доступ через нього, а після розблокування встановити новий PIN. Без recovery-slot втрата єдиного доступного ключа означає втрату vault."
        )
        FaqItem(
            question = "Чи можна робити скріншоти Encly?",
            answer = "Ні. Захист екрана увімкнений постійно: Encly блокує скріншоти, запис екрана та незахищений preview у списку нещодавніх застосунків."
        )
        FaqItem(
            question = "Що відбувається після згортання застосунку?",
            answer = "Encly зберігає поточні зміни, закриває зашифровану базу та очищає сесійний ключ. Після повернення потрібне повторне розблокування."
        )

        TitleCard("Налаштування", modifier = Modifier.padding(horizontal = 20.dp))

        FaqItem(
            question = "Як змінити PIN?",
            answer = "Відкрийте Налаштування → Безпека, підтвердьте поточний PIN, а потім двічі введіть новий."
        )
        FaqItem(
            question = "Як увімкнути або вимкнути біометрію?",
            answer = "У Налаштування → Безпека використайте перемикач біометрії. Створення або видалення biometric-slot потребує сильної біометричної автентифікації."
        )
        FaqItem(
            question = "Як змінити тему?",
            answer = "У Налаштування → Зовнішній вигляд можна вибрати системну, світлу або темну тему та, на підтримуваних Android-пристроях, динамічні кольори."
        )

        TitleCard("Вирішення проблем", modifier = Modifier.padding(horizontal = 20.dp))

        FaqItem(
            question = "Encly повідомляє про пошкодження vault",
            answer = "Encly навмисно не створює порожню базу поверх втраченого або нечитабельного vault. Не підтверджуйте повне очищення, якщо вам ще потрібні локальні дані."
        )
        FaqItem(
            question = "Як повідомити про помилку?",
            answer = "Відкрийте «Про додаток» і скористайтеся зворотним зв'язком або напишіть розробнику. Не додавайте recovery seed, PIN або приватний текст нотаток до bug report."
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FaqItem(
    question: String,
    answer: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        onClick = { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .padding(horizontal = 15.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Lucide.HandHelping,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = question,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = if (expanded) Lucide.ChevronUp else Lucide.ChevronDown,
                    contentDescription = if (expanded) "Згорнути" else "Розгорнути",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            if (expanded) {
                Text(
                    text = answer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 12.dp, start = 44.dp)
                )
            }
        }
    }
}
