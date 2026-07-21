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
                description = "Знайдіть відповіді на запитання про використання додатку, синхронізацію, безпеку та інші корисні поради."
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
        modifier = Modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Core features
        TitleCard("Основні функції", modifier = Modifier.padding(horizontal = 20.dp))

        FaqItem(
            question = "Як створити нову нотатку?",
            answer = "Натисніть кнопку '+' на головному екрані або у правому нижньому куті. Оберіть тип контенту та почніть писати."
        )

        FaqItem(
            question = "Як додати блоки в нотатку?",
            answer = "В режимі редагування натисніть кнопку '+' між блоками або в кінці нотатки. Оберіть тип блоку: текст, заголовок, список, зображення або посилання."
        )

        FaqItem(
            question = stringResource(R.string.faq_tags_title),
            answer = stringResource(R.string.faq_tags_answer)
        )

        FaqItem(
            question = "Як видалити тег?",
            answer = "Перейдіть у меню → Теги. Натисніть на тег, який хочете видалити, та підтвердіть дію. Тег буде видалений з усіх нотаток."
        )

        FaqItem(
            question = "Як сортувати нотатки?",
            answer = "На головному екрані натисніть іконку сортування. Оберіть спосіб: за датою створення, редагування, назвою або за зростанням/спаданням."
        )

        FaqItem(
            question = "Як шукати нотатки?",
            answer = "Натисніть іконку пошуку на головному екрані. Введіть ключові слова - пошук працює по назві та вмісту нотаток."
        )

        // Tasks
        TitleCard("Завдання", modifier = Modifier.padding(horizontal = 20.dp))

        FaqItem(
            question = stringResource(R.string.faq_tasks_title),
            answer = stringResource(R.string.faq_tasks_answer)
        )

        FaqItem(
            question = "Як встановити нагадування для завдання?",
            answer = "При створенні або редагуванні завдання натисніть 'Встановити нагадування' та оберіть дату і час. Додаток надішле push-сповіщення."
        )

        FaqItem(
            question = "Де переглянути виконані завдання?",
            answer = "У розділі 'Завдання' є вкладки 'Активні' та 'Виконані'. Виконані завдання автоматично переносяться у відповідну вкладку."
        )

        // Security
        TitleCard("Безпека та конфіденційність", modifier = Modifier.padding(horizontal = 20.dp))

        FaqItem(
            question = stringResource(R.string.faq_encryption_title),
            answer = stringResource(R.string.faq_encryption_answer)
        )

        FaqItem(
            question = "Як встановити пін-код або відбиток пальця?",
            answer = "Перейдіть у Налаштування → Безпека. Увімкніть захист пін-кодом або біометрією та встановіть код доступу."
        )

        FaqItem(
            question = "Що робити, якщо забув пін-код?",
            answer = "Ваші дані будуть захищені. Якщо ви забули пін-код, ви можете скинути його, але це призведе до втрати всіх даних у додатку. Рекомендується регулярно робити резервні копії нотаток."
        )

        FaqItem(
            question = "Як приховати додаток з нещодавніх?",
            answer = "Увімкніть 'Захист вмісту' в налаштуваннях безпеки. Це також заборонить створення скріншотів додатку."
        )

        // Settings and personalization
        TitleCard("Налаштування та персоналізація", modifier = Modifier.padding(horizontal = 20.dp))

        FaqItem(
            question = "Як змінити тему додатку?",
            answer = "Перейдіть у Налаштування → Зовнішній вигляд → Тема. Оберіть світлу, темну або автоматичну тему відповідно до системи."
        )

        FaqItem(
            question = "Що таке динамічні кольори?",
            answer = "Динамічні кольори адаптують колірну схему додатку під обої вашого телефону (доступно на Android 12+). Увімкніть у налаштуваннях зовнішнього вигляду."
        )

        FaqItem(
            question = "Як увімкнути простий режим редагування?",
            answer = "У налаштуваннях знайдіть 'Простий режим редагування'. Це відключить динамічні блоки та зробить редагування схожим на звичайний текстовий редактор."
        )

        // Problems and solutions
        TitleCard("Вирішення проблем", modifier = Modifier.padding(horizontal = 20.dp))

        FaqItem(
            question = "Додаток працює повільно або зависає",
            answer = "Спробуйте перезапустити додаток. Якщо проблема залишається, очистіть кеш у налаштуваннях → Про додаток або переустановіть додаток."
        )

        FaqItem(
            question = "Зображення не завантажуються",
            answer = "Перевірте дозволи додатку на доступ до сховища в налаштуваннях телефону. Також переконайтеся, що файли не були видалені з пристрою."
        )

        FaqItem(
            question = "Як повідомити про помилку?",
            answer = "Перейдіть у меню → Підтримка розробника → Зворотний зв'язок. Виберіть тип 'Повідомити про помилку' та опишіть проблему детально."
        )

        FaqItem(
            question = "Пропозиції по покращенню додатку",
            answer = "Ми завжди раді вашим ідеям! Використовуйте форму зворотного зв'язку в розділі підтримки або напишіть на pasichdev@outlook.com"
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
