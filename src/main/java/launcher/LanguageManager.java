package launcher;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputControl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Quan ly da ngon ngu nhe (Tieng Viet / English / Nga / Trung / Han / An Do / Nhat Ban) cho ME Launcher.
 * Khong dung thu vien ngoai, tu dong dich cac Node giao dien theo cau hinh da luu.
 */
public class LanguageManager {

    private static final String PROP_TEXT_KEY = "me.i18n.textKey";
    private static final String PROP_PROMPT_KEY = "me.i18n.promptKey";

    // Thu tu hien thi trong ComboBox Cai dat
    private static final Map<String, String> CODE_TO_DISPLAY = new LinkedHashMap<>();
    private static final Map<String, String> DISPLAY_TO_CODE = new LinkedHashMap<>();

    // key (ban tieng Viet goc) -> (langCode -> ban dich)
    private static final Map<String, Map<String, String>> DICT = new LinkedHashMap<>();
    // bat ky ban dich nao -> key goc (phong truong hop Node chua gan property)
    private static final Map<String, String> ANY_TO_KEY = new LinkedHashMap<>();

    static {
        registerLanguage("vi", "Tiếng Việt");
        registerLanguage("en", "English");
        registerLanguage("ru", "Русский");
        registerLanguage("zh", "中文 (简体)");
        registerLanguage("ko", "한국어");
        registerLanguage("hi", "हिन्दी");
        registerLanguage("ja", "日本語");

        // loading.fxml
        put("Dang khoi dong...",
                "Starting...",
                "Запуск...",
                "正在启动...",
                "시작하는 중...",
                "शुरू हो रहा है...",
                "起動中...");

        // launcher.fxml & sidebar
        put("Trang chu",
                "Home",
                "Главная",
                "主页",
                "홈",
                "होम",
                "ホーム");
        put("Mod Loader",
                "Mod Loader",
                "Мод-лоадеры",
                "模组加载器",
                "모드 로더",
                "मॉड लोडर",
                "Modローダー");
        put("Thu muc game",
                "Game folder",
                "Папка игры",
                "游戏目录",
                "게임 폴더",
                "गेम फ़ोल्डर",
                "ゲームフォルダ");
        put("Console",
                "Console",
                "Консоль",
                "控制台",
                "콘솔",
                "कंसोल",
                "コンソール");
        put("Chua dang nhap",
                "Not logged in",
                "Не выполнен вход",
                "未登录",
                "로그인되지 않음",
                "लॉग इन नहीं है",
                "未ログイン");
        put("Dang nhap",
                "Login",
                "Войти",
                "登录",
                "로그인",
                "लॉग इन",
                "ログイン");
        put("Doi tai khoan",
                "Switch account",
                "Сменить аккаунт",
                "切换账号",
                "계정 전환",
                "खाता बदलें",
                "アカウント切替");
        put("Cai dat",
                "Settings",
                "Настройки",
                "设置",
                "설정",
                "सेटिंग्स",
                "設定");
        put("Chao mung tro lai!",
                "Welcome back!",
                "С возвращением!",
                "欢迎回来！",
                "다시 오신 것을 환영합니다!",
                "वापस स्वागत है!",
                "おかえりなさい！");
        put("San sang phieu luu trong the gioi Minecraft",
                "Ready for your next Minecraft adventure",
                "Готовы к новому приключению в Minecraft",
                "准备好开启你的 Minecraft 冒险吧",
                "마인크래프트 모험을 시작할 준비가 되었습니다",
                "अपने अगले Minecraft रोमांच के लिए तैयार हैं",
                "Minecraftの冒険を始める準備ができました");
        put("San sang",
                "Ready",
                "Готово",
                "就绪",
                "준비됨",
                "तैयार",
                "準備完了");
        put("CHOI",
                "PLAY",
                "ИГРАТЬ",
                "开始游戏",
                "플레이",
                "खेलें",
                "プレイ");

        // about.fxml
        put("Phien ban 1.0.0",
                "Version 1.0.0",
                "Версия 1.0.0",
                "版本 1.0.0",
                "버전 1.0.0",
                "संस्करण 1.0.0",
                "バージョン 1.0.0");
        put("Launcher Minecraft tu viet bang Java + JavaFX",
                "Custom Minecraft Launcher built with Java + JavaFX",
                "Лаунчер Minecraft на Java + JavaFX",
                "基于 Java + JavaFX 开发的 Minecraft 启动器",
                "Java + JavaFX로 제작된 Minecraft 런처",
                "Java + JavaFX से बना कस्टम Minecraft लॉन्चर",
                "Java + JavaFX製カスタムMinecraftランチャー");
        put("Dang nhap qua tai khoan Microsoft that (OAuth device code flow)",
                "Sign in with Microsoft account (OAuth device code flow)",
                "Вход через аккаунт Microsoft (OAuth device code flow)",
                "使用 Microsoft 账号登录（OAuth 设备代码流程）",
                "Microsoft 계정으로 로그인 (OAuth 장치 코드 방식)",
                "Microsoft खाते से साइन इन करें (OAuth डिवाइस कोड फ़्लो)",
                "Microsoftアカウントでサインイン（OAuthデバイスコード方式）");

        // settings.fxml
        put("Cài đặt (Settings)",
                "Settings (ME Launcher)",
                "Настройки (ME Launcher)",
                "设置 (ME Launcher)",
                "설정 (ME Launcher)",
                "सेटिंग्स (ME Launcher)",
                "設定 (ME Launcher)");
        put("Giới thiệu",
                "About",
                "О программе",
                "关于",
                "정보",
                "परिचय",
                "情報");
        put("Thư mục lưu file game (Directory):",
                "Game directory:",
                "Папка игры (Directory):",
                "游戏目录 (Directory):",
                "게임 폴더 (Directory):",
                "गेम फ़ोल्डर (Directory):",
                "ゲームフォルダ (Directory):");
        put("vd: D:\\MinecraftData (để trống = thư mục 'game' mặc định)",
                "e.g. D:\\MinecraftData (leave empty for default 'game' folder)",
                "напр. D:\\MinecraftData (пусто = папка 'game' по умолчанию)",
                "例如 D:\\MinecraftData（留空 = 默认 'game' 目录）",
                "예: D:\\MinecraftData (비워 두면 기본 'game' 폴더 사용)",
                "उदा. D:\\MinecraftData (खाली छोड़ें = डिफ़ॉल्ट 'game' फ़ोल्डर)",
                "例: D:\\MinecraftData（空欄 = デフォルトの 'game' フォルダ）");
        put("Browse...",
                "Browse...",
                "Обзор...",
                "浏览...",
                "찾아보기...",
                "ब्राउज़...",
                "参照...");
        put("Độ phân giải (Resolution):",
                "Resolution:",
                "Разрешение (Resolution):",
                "游戏分辨率 (Resolution):",
                "해상도 (Resolution):",
                "रिज़ॉल्यूशन (Resolution):",
                "解像度 (Resolution):");
        put("Fullscreen (Toàn màn hình)",
                "Fullscreen",
                "Полный экран",
                "全屏模式",
                "전체 화면",
                "फुलस्क्रीन",
                "フルスクリーン");
        put("Danh sách phiên bản (Version list):",
                "Version list:",
                "Список версий:",
                "版本列表:",
                "버전 목록:",
                "संस्करण सूची:",
                "バージョン一覧:");
        put("Hiện Snapshots",
                "Show Snapshots",
                "Показать Snapshots",
                "显示快照版 (Snapshots)",
                "스냅샷 표시",
                "स्नैपशॉट दिखाएं",
                "スナップショットを表示");
        put("Hiện bản Mod (NeoForge/Forge/OptiFine)",
                "Show Modded (NeoForge/Forge/OptiFine)",
                "Показать моды (NeoForge/Forge/OptiFine)",
                "显示模组版 (NeoForge/Forge/OptiFine)",
                "모드 버전 표시 (NeoForge/Forge/OptiFine)",
                "मॉड संस्करण दिखाएं (NeoForge/Forge/OptiFine)",
                "Mod版を表示 (NeoForge/Forge/OptiFine)");
        put("Hiện Beta",
                "Show Beta",
                "Показать Beta",
                "显示 Beta 版",
                "Beta 표시",
                "Beta दिखाएं",
                "Betaを表示");
        put("Hiện Alpha",
                "Show Alpha",
                "Показать Alpha",
                "显示 Alpha 版",
                "Alpha 표시",
                "Alpha दिखाएं",
                "Alphaを表示");
        put("Hiện bản cũ (≤ 1.13)",
                "Show Old Releases (≤ 1.13)",
                "Старые версии (≤ 1.13)",
                "显示旧版本 (≤ 1.13)",
                "구버전 표시 (≤ 1.13)",
                "पुराने संस्करण दिखाएं (≤ 1.13)",
                "旧バージョンを表示 (≤ 1.13)");
        put("Java Arguments (Lệnh Java khi chạy - JVM Flags):",
                "Java Arguments (JVM Flags):",
                "Аргументы Java (JVM Flags):",
                "Java 参数 (JVM Flags):",
                "Java 인수 (JVM Flags):",
                "Java आर्ग्युमेंट्स (JVM Flags):",
                "Java引数 (JVM Flags):");
        put("Minecraft Arguments (Tham số vào game tùy chỉnh):",
                "Minecraft Arguments (Custom game flags):",
                "Аргументы Minecraft:",
                "Minecraft 启动参数:",
                "Minecraft 인수:",
                "Minecraft आर्ग्युमेंट्स:",
                "Minecraft引数:");
        put("Đường dẫn Java tùy chỉnh (Java path - để trống = tự động tải):",
                "Custom Java path (leave empty for auto-download):",
                "Путь к Java (пусто = автозагрузка):",
                "自定义 Java 路径（留空 = 自动下载）:",
                "사용자 지정 Java 경로 (비워 두면 자동 다운로드):",
                "कस्टम Java पथ (खाली छोड़ें = ऑटो डाउनलोड):",
                "カスタムJavaパス（空欄 = 自動ダウンロード）:");
        put("Phân bổ bộ nhớ RAM (Memory allocation - MB):",
                "Memory allocation (MB):",
                "Выделение ОЗУ (RAM - MB):",
                "内存分配 (RAM - MB):",
                "메모리 할당 (RAM - MB):",
                "RAM आवंटन (MB):",
                "メモリ割り当て (MB):");
        put("Gợi ý theo máy",
                "Auto-suggest",
                "Рекомендовать",
                "自动推荐",
                "자동 추천",
                "ऑटो सुझाव",
                "自動推奨");
        put("RAM tối thiểu (MB):",
                "Minimum RAM (MB):",
                "Мин. ОЗУ (MB):",
                "最小内存 (MB):",
                "최소 RAM (MB):",
                "न्यूनतम RAM (MB):",
                "最小RAM (MB):");
        put("Chế độ chơi (chỉnh đồ họa vanilla trong options.txt):",
                "Performance mode (adjusts vanilla options.txt):",
                "Режим графики (options.txt):",
                "性能模式（自动调整 options.txt）:",
                "성능 모드 (options.txt 조정):",
                "प्रदर्शन मोड (options.txt समायोजित करता है):",
                "パフォーマンスモード (options.txt調整):");
        put("Giao diện tối (dark theme)",
                "Dark theme",
                "Тёмная тема",
                "深色主题",
                "다크 테마",
                "डार्क थीम",
                "ダークテーマ");
        put("Hành vi Launcher khi mở game:",
                "Launcher behavior on game launch:",
                "Поведение лаунчера при запуске игры:",
                "启动游戏时的启动器行为:",
                "게임 실행 시 런처 동작:",
                "गेम शुरू होने पर लॉन्चर व्यवहार:",
                "ゲーム起動時のランチャー動作:");
        put("Ẩn Launcher khi mở game",
                "Hide Launcher when game starts",
                "Скрывать лаунчер при запуске игры",
                "启动游戏时隐藏启动器",
                "게임 시작 시 런처 숨기기",
                "गेम शुरू होने पर लॉन्चर छिपाएं",
                "ゲーム起動時にランチャーを隠す");
        put("Hiện (giữ) Launcher khi mở game",
                "Keep Launcher open",
                "Оставлять лаунчер открытым",
                "启动游戏时保持显示",
                "런처 창 유지하기",
                "लॉन्चर खुला रखें",
                "ランチャーを表示したままにする");
        put("Chế độ RAM Launcher (Low / High):",
                "Launcher RAM mode (Low / High):",
                "Режим ОЗУ лаунчера (Low / High):",
                "启动器内存模式 (Low / High):",
                "런처 RAM 모드 (Low / High):",
                "लॉन्चर RAM मोड (Low / High):",
                "ランチャーRAMモード (Low / High):");
        put("Low (Ăn ít RAM <150MB)",
                "Low (Low RAM <150MB)",
                "Low (Экономия ОЗУ <150MB)",
                "Low（低内存占用 <150MB）",
                "Low (저메모리 <150MB)",
                "Low (कम RAM <150MB)",
                "Low（省メモリ <150MB）");
        put("High (Đủ nền & hiệu ứng 200-400MB)",
                "High (Full background & effects 200-400MB)",
                "High (Фон и эффекты 200-400MB)",
                "High（完整背景与特效 200-400MB）",
                "High (배경 및 효과 200-400MB)",
                "High (पूर्ण बैकग्राउंड और प्रभाव 200-400MB)",
                "High（背景・エフェクト有効 200-400MB）");

        // UI Layout & Background trong Settings
        put("Giao diện & Hình nền (Appearance & Background):",
                "Appearance & Background:",
                "Интерфейс и фон (Appearance):",
                "外观与背景 (Appearance):",
                "외관 및 배경 (Appearance):",
                "रूप और बैकग्राउंड (Appearance):",
                "外観と背景 (Appearance):");
        put("Bố cục giao diện (UI Layout):",
                "UI Layout:",
                "Макет интерфейса:",
                "界面布局:",
                "UI 레이아웃:",
                "UI लेआउट:",
                "UIレイアウト:");
        put("Cổ điển (Sidebar + Thanh đáy)",
                "Classic (Sidebar + Bottom bar)",
                "Классический (Боковая панель + Низ)",
                "经典布局（侧边栏 + 底部栏）",
                "클래식 (사이드바 + 하단 바)",
                "क्लासिक (साइडबार + निचला बार)",
                "クラシック（サイドバー + 下部バー）");
        put("Tối giản (Thanh icon + Nút Chơi giữa)",
                "Modern (Icon rail + Center Play)",
                "Современный (Иконки + Кнопка в центре)",
                "极简布局（图标栏 + 居中开始按钮）",
                "모던 (아이콘 바 + 중앙 플레이)",
                "मॉडर्न (आइकन रेल + केंद्र प्ले)",
                "モダン（アイコンバー + 中央プレイ）");
        put("Hình nền Launcher (Background):",
                "Launcher Background:",
                "Фон лаунчера (Background):",
                "启动器背景图 (Background):",
                "런처 배경화면 (Background):",
                "लॉन्चर बैकग्राउंड (Background):",
                "ランチャー背景画像 (Background):");
        put("Mặc định (để trống = dùng hình nền mặc định)",
                "Default (leave empty for default background)",
                "По умолчанию (пусто = стандартный фон)",
                "默认（留空 = 使用默认背景）",
                "기본값 (비워 두면 기본 배경 사용)",
                "डिफ़ॉल्ट (खाली छोड़ें = डिफ़ॉल्ट बैकग्राउंड)",
                "デフォルト（空欄 = デフォルト背景）");
        put("Chọn ảnh...",
                "Choose image...",
                "Выбрать...",
                "选择图片...",
                "이미지 선택...",
                "चित्र चुनें...",
                "画像を選択...");
        put("Mặc định",
                "Default",
                "Сбросить",
                "恢复默认",
                "기본값",
                "डिफ़ॉल्ट",
                "デフォルト");
        put("Độ tối lớp phủ nền (Overlay):",
                "Background overlay darkness:",
                "Затемнение фона (Overlay):",
                "背景遮罩暗度 (Overlay):",
                "배경 오버레이 어둡기:",
                "बैकग्राउंड ओवरले गहराई:",
                "背景オーバーレイの暗さ:");
        put("Xem trước hình nền",
                "Background preview",
                "Предпросмотр фона",
                "背景预览",
                "배경 미리보기",
                "बैकग्राउंड पूर्वावलोकन",
                "背景プレビュー");

        put("Ngôn ngữ (Language):",
                "Language:",
                "Язык (Language):",
                "语言 (Language):",
                "언어 (Language):",
                "भाषा (Language):",
                "言語 (Language):");
        put("Tự hiện Console khi Launcher hoặc game bị lỗi",
                "Auto-show Console when Launcher or game crashes",
                "Автоматически открывать консоль при ошибке",
                "启动器或游戏崩溃时自动显示控制台",
                "런처 또는 게임 오류 시 콘솔 자동 표시",
                "त्रुटि होने पर कंसोल स्वतः दिखाएं",
                "エラー時にコンソールを自動表示");
        put("Mở Console",
                "Open Console",
                "Открыть консоль",
                "打开控制台",
                "콘솔 열기",
                "कंसोल खोलें",
                "コンソールを開く");
        put("Đặt lại mặc định (Reset)",
                "Reset Defaults",
                "Сбросить настройки",
                "恢复默认设置",
                "기본값으로 초기화",
                "डिफ़ॉल्ट रीसेट करें",
                "デフォルトに戻す");
        put("Hủy",
                "Cancel",
                "Отмена",
                "取消",
                "취소",
                "रद्द करें",
                "キャンセル");
        put("Lưu (Save)",
                "Save",
                "Сохранить",
                "保存",
                "저장",
                "सहेजें",
                "保存");
    }

    private LanguageManager() {
    }

    private static void registerLanguage(String code, String displayName) {
        CODE_TO_DISPLAY.put(code, displayName);
        DISPLAY_TO_CODE.put(displayName, code);
    }

    private static void put(String vi, String en, String ru, String zh, String ko, String hi, String ja) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("vi", vi);
        row.put("en", en);
        row.put("ru", ru);
        row.put("zh", zh);
        row.put("ko", ko);
        row.put("hi", hi);
        row.put("ja", ja);
        DICT.put(vi, row);
        for (String translated : row.values()) {
            ANY_TO_KEY.putIfAbsent(translated, vi);
        }
    }

    public static List<String> getDisplayNames() {
        return new ArrayList<>(CODE_TO_DISPLAY.values());
    }

    public static String displayNameForCode(String code) {
        String normalized = normalizeCode(code);
        return CODE_TO_DISPLAY.getOrDefault(normalized, "Tiếng Việt");
    }

    public static String codeFromDisplayName(String displayName) {
        if (displayName == null) return "vi";
        return DISPLAY_TO_CODE.getOrDefault(displayName, "vi");
    }

    public static String normalizeCode(String code) {
        if (code == null || code.isBlank()) return "vi";
        String lower = code.trim().toLowerCase();
        return CODE_TO_DISPLAY.containsKey(lower) ? lower : "vi";
    }

    public static String getCurrentLanguage() {
        try {
            return normalizeCode(ConfigManager.get().getLanguage());
        } catch (Throwable ignored) {
            return "vi";
        }
    }

    public static boolean isEnglish() {
        return "en".equals(getCurrentLanguage());
    }

    /** Dich nhanh theo khoa cho Console va tieu de cua so. */
    public static String t(String key) {
        String lang = getCurrentLanguage();
        return switch (key) {
            case "console.title" -> pick(lang,
                    "ME Launcher - Bảng điều khiển lỗi (Console)",
                    "ME Launcher - Error Console",
                    "ME Launcher - Консоль ошибок",
                    "ME Launcher - 错误控制台",
                    "ME Launcher - 오류 콘솔",
                    "ME Launcher - त्रुटि कंसोल",
                    "ME Launcher - エラーコンソール");
            case "console.header.ready" -> pick(lang,
                    "Nhật ký hệ thống ME Launcher & Minecraft",
                    "Launcher & Minecraft Log Console",
                    "Журнал ME Launcher и Minecraft",
                    "ME Launcher 与 Minecraft 系统日志",
                    "ME Launcher 및 Minecraft 로그 콘솔",
                    "ME Launcher और Minecraft लॉग कंसोल",
                    "ME Launcher & Minecraft ログコンソール");
            case "console.header.error" -> pick(lang,
                    "Phát hiện lỗi! Xem chi tiết bên dưới:",
                    "An error occurred! Details below:",
                    "Обнаружена ошибка! Подробности ниже:",
                    "检测到错误！详情如下：",
                    "오류가 발생했습니다! 아래 세부 정보를 확인하세요:",
                    "त्रुटि हुई! विवरण नीचे देखें:",
                    "エラーが発生しました！詳細は以下を確認してください：");
            case "console.copy" -> pick(lang,
                    "Copy Log", "Copy Log", "Копировать", "复制日志", "로그 복사", "लॉग कॉपी करें", "ログをコピー");
            case "console.copied" -> pick(lang,
                    "Đã Copy!", "Copied!", "Скопировано!", "已复制！", "복사됨!", "कॉपी हो गया!", "コピーしました！");
            case "console.openLogs" -> pick(lang,
                    "Mở thư mục logs", "Open logs folder", "Папка логов", "打开日志目录", "로그 폴더 열기", "लॉग फ़ोल्डर खोलें", "ログフォルダを開く");
            case "console.clear" -> pick(lang,
                    "Xóa", "Clear", "Очистить", "清空", "지우기", "साफ़ करें", "クリア");
            case "console.close" -> pick(lang,
                    "Đóng", "Close", "Закрыть", "关闭", "닫기", "बंद करें", "閉じる");
            case "settings.title" -> pick(lang,
                    "Cài đặt", "Settings", "Настройки", "设置", "설정", "सेटिंग्स", "設定");
            case "about.title" -> pick(lang,
                    "Giới thiệu", "About", "О программе", "关于", "정보", "परिचय", "情報");
            default -> key;
        };
    }

    private static String pick(String lang, String vi, String en, String ru, String zh, String ko, String hi, String ja) {
        return switch (lang) {
            case "en" -> en;
            case "ru" -> ru;
            case "zh" -> zh;
            case "ko" -> ko;
            case "hi" -> hi;
            case "ja" -> ja;
            default -> vi;
        };
    }

    /** Dich 1 chuoi bat ky theo tu dien (neu co), dung cho Tooltip / trang thai dong. */
    public static String translateText(String baseOrTranslated) {
        if (baseOrTranslated == null) return "";
        String key = ANY_TO_KEY.get(baseOrTranslated);
        if (key == null) return baseOrTranslated;
        Map<String, String> row = DICT.get(key);
        if (row == null) return baseOrTranslated;
        return row.getOrDefault(getCurrentLanguage(), key);
    }

    /** Quet va dich toan bo Node trong 1 Scene theo ngon ngu hien tai. */
    public static void applyToScene(Scene scene) {
        if (scene == null || scene.getRoot() == null) return;
        applyToNode(scene.getRoot(), getCurrentLanguage());
    }

    /** Giu lai ham cu de tuong thich nguoc. */
    public static void applyToNode(Node node, boolean toEnglish) {
        applyToNode(node, toEnglish ? "en" : "vi");
    }

    public static void applyToNode(Node node) {
        applyToNode(node, getCurrentLanguage());
    }

    public static void applyToNode(Node node, String langCode) {
        if (node == null) return;
        String targetLang = normalizeCode(langCode);

        if (node instanceof Labeled labeled) {
            String text = labeled.getText();
            String key = null;
            if (text != null && ANY_TO_KEY.containsKey(text)) {
                key = ANY_TO_KEY.get(text);
                labeled.getProperties().put(PROP_TEXT_KEY, key);
            } else if (labeled.getProperties().get(PROP_TEXT_KEY) instanceof String storedKey) {
                key = storedKey;
            }
            if (key != null) {
                Map<String, String> row = DICT.get(key);
                if (row != null && row.containsKey(targetLang)) {
                    labeled.setText(row.get(targetLang));
                }
            }
        } else if (node instanceof TextInputControl input) {
            String prompt = input.getPromptText();
            String key = null;
            if (prompt != null && ANY_TO_KEY.containsKey(prompt)) {
                key = ANY_TO_KEY.get(prompt);
                input.getProperties().put(PROP_PROMPT_KEY, key);
            } else if (input.getProperties().get(PROP_PROMPT_KEY) instanceof String storedKey) {
                key = storedKey;
            }
            if (key != null) {
                Map<String, String> row = DICT.get(key);
                if (row != null && row.containsKey(targetLang)) {
                    input.setPromptText(row.get(targetLang));
                }
            }
        }

        if (node instanceof ScrollPane scrollPane && scrollPane.getContent() != null) {
            applyToNode(scrollPane.getContent(), targetLang);
        }

        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                applyToNode(child, targetLang);
            }
        }
    }
}