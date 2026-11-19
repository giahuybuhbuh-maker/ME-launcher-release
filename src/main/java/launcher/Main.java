package launcher;

/**
 * Diem khoi dau thuc su cua chuong trinh.
 *
 * Ly do tach rieng khoi LauncherApplication: khi dong goi thanh 1 file .jar
 * chay doc lap (fat-jar) va chay bang "java -jar", neu class co main() lai
 * ke thua truc tiep javafx.application.Application, Java se bao loi
 * "JavaFX runtime components are missing" du day du thu vien co san.
 * Tach Main rieng (khong lien quan gi den JavaFX) giup tranh loi nay.
 */
public class Main {
    public static void main(String[] args) {
        LauncherApplication.main(args);
    }
}
