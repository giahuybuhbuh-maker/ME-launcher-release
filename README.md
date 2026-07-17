# ME Launcher

Launcher Minecraft tu viet bang Java + JavaFX.

## Yeu cau

- JDK 25 tro len (Adoptium Temurin hoac Oracle JDK deu duoc)
- Gradle (da cai san tren may)

## Chay thu

```bash
gradle run
```

Neu may chua co san JDK 25, KHONG can tu cai - Gradle se tu dong tai
ve (qua plugin foojay-resolver trong settings.gradle) trong lan chay
dau tien. Lan dau se lau hon binh thuong vi phai tai JDK + JavaFX.

Muon dung `./gradlew run` cho tien (khong can cai Gradle rieng), chay
`gradle wrapper` mot lan de Gradle tu tao file wrapper cho du an.

## Dong goi thanh .exe (app-image)

```bash
gradle jpackageImage
```

Lenh nay tao ra 1 thu muc portable tai `build/dist/ME Launcher/`,
ben trong co san `ME Launcher.exe` + rieng 1 ban Java runtime rut gon -
nguoi dung cuoi chi can double-click, **khong can cai Java** gi ca.

Yeu cau: lenh `jpackage` phai dung duoc tu terminal (co san trong
JDK tu ban 14+, nam trong thu muc `bin` cua JDK - neu JDK da nam
trong PATH thi khong can lam gi them).

Day la kieu **app-image** (portable, khong co trinh cai dat). Neu
sau nay muon xuat installer that su (`.exe`/`.msi` co wizard "Next >
Next > Install"), can cai them WiX Toolset (v3.14+) tren may build,
roi doi `--type app-image` thanh `--type exe` trong `build.gradle`.

Icon: neu bo file `icon.ico` that vao `src/main/resources/assets/`,
task se tu dong dung lam icon cho file .exe (khong can sua gi them).

## Chuan bi dang nhap Microsoft (BAT BUOC truoc khi dung nut Dang nhap)

`AuthManager.java` can 1 Azure App Registration cua rieng ban:

1. Vao https://portal.azure.com -> tim "Microsoft Entra ID" -> **App
   registrations** -> **New registration**.
2. Dat ten bat ky, chon "Accounts in any organizational directory... and
   personal Microsoft accounts", **de trong Redirect URI**, bam Register.
3. Vao **Authentication** -> Add a platform -> **Mobile and desktop
   applications** -> chon URI co san
   `https://login.microsoftonline.com/common/oauth2/nativeclient` ->
   bam **Allow public client flows = Yes** -> Save.
4. Copy **Application (client) ID** o trang Overview, dan vao
   `CLIENT_ID` trong `AuthManager.java` (thay cho dong
   `"DIEN_CLIENT_ID_CUA_BAN_VAO_DAY"`).

**CANH BAO QUAN TRONG (doc truoc khi lam, tranh mat cong):** theo cac
bao cao gan day tren dien dan cua Microsoft, cac Azure App **moi tao**
gio can duoc Microsoft **duyet rieng** moi dung duoc quyen
`XboxLive.signin` (dien vao form o day:
https://aka.ms/AppRegInfo) - neu khong xin duoc, goi API se tra loi
"Invalid app registration". Mot so nguoi con duoc huong dan phai dang
ky Xbox Developer Program (ID@Xbox) - thu tuc danh cho nha phat trien
game, khong danh rieng cho launcher hobby. Cac launcher lon (MultiMC,
Prism Launcher...) dang dung app ID duoc tao/duyet **truoc** khi chinh
sach nay siet lai, nen ho khong gap van de nay. Noi cach khac: code
trong `AuthManager` la dung, nhung **buoc xin quyen tu Microsoft co
the la rao can lon nhat**, nam ngoai kha nang cua minh (Claude) giup
duoc - neu bi loi ngay tu buoc dau, rat co the la do cho nay, khong
phai do code.

## Cau truc thu muc

| Thu muc | Vai tro |
|---|---|
| `src/main/java/launcher` | Logic chinh: dieu khien UI, quan ly cau hinh, tai file, chay game... |
| `src/main/java/model` | Cac lop du lieu (cau hinh, phien ban, tai khoan...) |
| `src/main/java/network` | Xu ly mang (HTTP, tai file) - **chua code, lam o Phase 1** |
| `src/main/resources/ui` | File `.fxml` giao dien |
| `src/main/resources/css` | File CSS giao dien |
| `src/main/resources/assets` | Anh giao dien (logo, background...) - bo file anh that vao day |
| `config/launcher.json` | Cau hinh launcher, tu tao khi chay lan dau |
| `game/` | Du lieu Minecraft (versions, libraries, mods, saves...) - giong thu muc `.minecraft` that |
| `runtime/java/` | Noi launcher tai & luu JRE phu hop cho tung phien ban Minecraft |
| `downloads/` | Noi tai file tam truoc khi xac thuc & chuyen vao `game/` |
| `logs/` | Log cua **launcher** (khac voi `game/logs/` la log cua **Minecraft**) |

## Tien do

- [x] Phase 0 - Khung du an, cua so chay duoc, he thong cau hinh + log co ban
- [x] Phase 1 - Model day du (`MinecraftVersion`, `JavaRuntime`, `UserProfile`) + network layer (`HttpClient`, `Downloader`, `FileDownloader`)
- [x] Phase 2 - `VersionManager` (doc version manifest tu Mojang) + `JavaManager` (tu tai JRE phu hop)
- [x] Phase 3 - `DownloadManager` + `FileManager` (tai & xac thuc libraries/assets)
- [x] Phase 4 - `ProcessManager` + `MinecraftLauncher` (build lenh & khoi chay game that)
- [x] Phase 5 - Dang nhap Microsoft that (OAuth device code flow)
- [x] Phase 6 - `settings.fxml`, `about.fxml`, `loading.fxml` + hoan thien UI/UX
- [x] Phase 7 (them, ngoai ke hoach goc) - Ho tro mod: `ModManager` (quan ly file mod) + `ModLoaderManager` (cai Fabric that). **Forge/NeoForge/Quilt CHUA lam** - xem ghi chu ben duoi.

## Ghi chu

- Du an dung Gradle (khong phai Maven) vi ban da du dinh thu muc `build/`
  trong ke hoach ban dau - do la quy uoc mac dinh cua Gradle.
- JavaFX 25.0.1 + JDK 25 la cap doi on dinh moi nhat tinh den giua 2026.
  Neu ban thich dung ban JDK/JavaFX khac, chi can sua trong `build.gradle`.
- `JavaManager` dua vao API tai JRE KHONG CHINH THUC cua Mojang (khong co
  tai lieu cong khai, chi duoc cong dong reverse-engineer). Chua goi thu
  duoc voi mang that - neu gap loi khi tai JRE, rat co the do cau truc
  JSON thuc te lech mot chut so voi du kien, bao minh de sua lai.
- Nut PLAY tu Phase 3 da tai THAT client.jar + libraries + assets vao
  `game/` (chua chay game - phan do la Phase 4). Bam PLAY se tai kha
  nhieu du lieu (vai tram MB tuy phien ban) trong lan dau tien.
- Phan loc thu vien theo he dieu hanh ("rules") va xu ly "natives" (LWJGL)
  la phan de sai nhat vi cach Mojang mo ta no da doi qua vai lan giua cac
  the he Minecraft. Neu thieu file .dll/.so trong `game/libraries`, bao
  minh kem ten phien ban de mo lai cho nay.
- **PLAY gio da chay Minecraft THAT.** Sau khi bam Dang nhap thanh cong,
  PLAY se: tai file thieu -> chuan bi JRE -> goi `MinecraftLauncher.launch()`
  that su. Cua so launcher se "dung" (cac nut bi khoa) trong luc Minecraft
  dang chay, vi `launch()` cho toi khi game dong lai - se lam cua so
  rieng/thu nho launcher luc choi o Phase 6.
- `AuthManager` CHUA luu refresh token ra dia - moi lan mo launcher deu
  phai bam Dang nhap lai. Co the toi uu sau: luu refreshToken (model
  `UserProfile` da co san field nay) va tu dang nhap lai am tham luc mo
  launcher, chi hoi lai khi refresh token het han.
- Cac ma loi Xbox Live (XErr) trong `AuthManager` KHONG duoc Microsoft
  cong bo chinh thuc - moi chi giai thich duoc 2 ma pho bien nhat
  (chua co ho so Xbox, tai khoan tre em), ma khac se chi hien so loi tho.
- `AuthManager` chi ho tro dang nhap Microsoft that va tu kiem tra quyen
  so huu game (`ownsMinecraft`) - khong co, va se khong them, duong nao
  de dang nhap ma khong co tai khoan da mua game.
- `MinecraftLauncher` hien CHI ho tro dinh dang "arguments" (JSON) tu ban
  1.13 tro di. Ban cu hon (dung chuoi "minecraftArguments") chua ho tro.
- Phase 6: them man hinh `loading.fxml` (hien ~1 giay luc mo app), dialog
  `settings.fxml` (RAM min/max, duong dan Java tuy chinh, dark theme) mo
  tu nut Cai dat, va `about.fxml` mo tu trong Cai dat. Neu dien duong dan
  Java tuy chinh, co the dien thang toi file `javaw.exe`/`java` hoac toi
  thu muc goc JRE deu duoc - `MinecraftLauncher` tu nhan dien ca 2 kieu.
- **Phase 7 (mod):** tick checkbox "Fabric" canh o chon phien ban roi bam
  PLAY - launcher se tu tim ban Fabric Loader on dinh moi nhat cho phien
  ban da chon, tai profile cua no, gop voi ban vanilla (them thu vien +
  doi mainClass), roi tai file & chay y het luong binh thuong. Mod (.jar)
  bo thu cong vao `game/mods/` - `ModManager` co san ham liet ke/bat/tat
  nhung CHUA co giao dien rieng de quan ly (chi moi la class dung duoc,
  giao dien danh sach mod de sau).
- **Forge/NeoForge/Quilt: CHUA lam, co chu dinh.** Fabric dung 1 API JSON
  don gian, on dinh, tra ve dung profile ke thua ban vanilla nen ghep duoc
  an toan. Forge dung "installer" rieng chay cac "processor" (thuc thi
  jar tuy y de patch/tai thu vien) - phuc tap hon han, de loi hon, va
  khong co API chuan de tu dong hoa ma khong can chay thu that. Neu can
  Forge, nen lam rieng 1 luot va kiem thu ky, khong nen ghep voi cach lam
  cua Fabric o day.
- Thu vien kieu Fabric (`"name"+"url"` Maven) KHONG co sha1 di kem (khac
  voi thu vien vanilla luon co) - `DownloadManager`/`MinecraftLauncher`
  chi kiem tra file co ton tai, khong xac thuc duoc noi dung nhu vanilla.
  Day la gioi han cua chinh API Fabric, khong phai thieu sot rieng cua
  launcher nay.
- **Fix hieu nang:** ban dau `FileManager.isValid()` hash lai SHA-1 toan
  bo file moi lan bam PLAY, kha cham voi phien ban co hang nghin file
  asset (co the toi vai phut du khong tai gi moi). Da doi sang kiem tra
  nhanh bang KICH THUOC file truoc (Mojang co san field "size"), chi rot
  ve hash khi khong biet truoc size (vd thu vien Fabric). SHA-1 van duoc
  xac thuc rieng ngay sau khi TAI file trong `Downloader`, khong doi cho
  nay - chi bo bot buoc kiem tra lai file DA co san moi lan bam PLAY.
- **Fix UUID:** API `minecraft/profile` tra ve "id" dang 32 ky tu hex
  KHONG co dau gach ngang, nhung Minecraft can dang UUID chuan (co gach,
  8-4-4-4-12) de tao GameProfile luc vao the gioi - thieu buoc nay se
  loi ngay khi tao the gioi/khoi tao nguoi choi. `AuthManager` gio tu
  them dau gach truoc khi luu vao `UserProfile`.
- **Fix gameDir (quan trong voi mod):** `--gameDir` truoc day tro vao
  goc project thay vi vao `game/`, nen Fabric tim thu muc `mods/` sai
  cho (`ModManager` bo mod vao `game/mods/`, con Fabric lai tim o
  `<goc-project>/mods/`). Da sua `game_directory` tro dung vao `game/`.
  Neu ban da tung chay thu va thay co thu muc `mods/`, `saves/` nam
  ngoai goc project (ngang hang voi `build.gradle`) - do la dau vet cua
  bug nay, co the xoa di, lan chay sau se dung vao `game/` het.
- Them **skin + cape** cho tai khoan Microsoft that: sau khi dang nhap,
  launcher tu tai anh mat (crop tu skin) hien canh ten trong thanh tren.
  Cape hien chi luu trong `UserProfile`, chua hien thi rieng.
- **Toc do khoi chay/chay game:** da them san "Aikar's flags" (tinh chinh
  G1GC, cong dong Minecraft dung tu 2018) vao moi lan chay, giup giam
  giat/lag do garbage collection - xem `performanceJvmFlags()` trong
  `MinecraftLauncher`. Muon tat, xoa dong `command.addAll(performanceJvmFlags());`.
  Muon nhanh hon nua: tick Fabric roi bo cac mod toi uu pho bien
  (Sodium, Lithium, Starlight...) vao `game/mods/` - launcher da ho tro
  Fabric + quan ly mod tu Phase 7, khong can code them gi.
