package com.sykeptical.hyperpop.service.logincode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoginCodeExtractorTest {

    private fun code(text: String, title: String? = null): String? =
        LoginCodeExtractor.extract(title, listOf(text))?.code

    private fun assertCode(expected: String, text: String, title: String? = null) {
        assertEquals("text: $text", expected, code(text, title))
    }

    private fun assertNoCode(text: String, title: String? = null) {
        assertNull("text: $text", code(text, title))
    }

    // --- English and common service formats ---

    @Test fun google() = assertCode("482913", "G-482913 is your Google verification code.", title = "Google")
    @Test fun codeIs() = assertCode("482913", "Your verification code is 482913")
    @Test fun codeColon() = assertCode("1234", "Your Uber code: 1234. Never share this code.")
    @Test fun whatsappGrouped() = assertCode("482913", "Your WhatsApp code: 482-913\nDon't share this code with others")
    @Test fun spacedGroups() = assertCode("48291374", "Your security code is 4829 1374")
    @Test fun microsoftLeadVerb() = assertCode("4821379", "Use 4821379 as Microsoft account security code")
    @Test fun telegram() = assertCode("48291", "Login code: 48291. Do not give this code to anyone, even if they say they are from Telegram!")
    @Test fun appleWebOtp() = assertCode("482913", "Your Apple Account code is: 482913. Don't share it with anyone.\n\n@apple.com #482913 %apple.com")
    @Test fun smsRetriever() = assertCode("482913", "<#> 482913 is your verification code FA+9qCX9VSu")
    @Test fun alphanumeric() = assertCode("F7K2P", "Your Steam Guard code: F7K2P")
    @Test fun lettersOnly() = assertCode("FQHTG", "Steam Guard code: FQHTG")
    @Test fun boldMarkdown() = assertCode("482913", "Your login code is *482913*")
    @Test fun doubleBold() = assertCode("482913", "Your login code is **482913**")
    @Test fun codeBeforeKeyword() = assertCode("739104", "739104 is your Instagram code. Don't share it.")
    @Test fun codeOnOwnLine() = assertCode("739104", "Use the code below to sign in:\n739104\nIt expires in 10 minutes.")
    @Test fun bareBodyWithTitle() = assertCode("739104", "739104", title = "Verification code")
    @Test fun codeWithTitleContext() = assertCode("739104", "Enter 739104 to continue", title = "Sign-in attempt")
    @Test fun bracketed() = assertCode("739104", "[Amazon] Your one-time password is (739104).")
    @Test fun gmailEmail() = assertCode(
        "552031",
        "Hi Alex, to finish signing in, enter the following verification code: 552031. This code expires in 15 minutes.",
        title = "Your Microsoft account verification code",
    )

    @Test fun indianBankTransactionOtp() = assertCode(
        "482913",
        "482913 is OTP for txn of INR 5000.00 at AMAZON on card XX1234. Valid till 10:30. Do not share OTP for security reasons.",
    )

    @Test fun otpWithAmountBeforeIt() = assertCode(
        "482913",
        "Your OTP for transaction of Rs 5,000.00 at AMAZON is 482913. Do not share it with anyone.",
    )

    @Test fun codeBeatsAmount() = assertCode("5678", "Your balance is $1234. Your verification code is 5678.")
    @Test fun codeBeatsCardDigits() = assertCode("773311", "Card ending 4821 was used. Verification code: 773311")
    @Test fun codeBeatsSenderShortCode() = assertCode("1234", "1234 is your Uber code", title = "22395")
    @Test fun codeBeatsPhone() = assertCode("482913", "Your OTP is 482913. For help call 1800 123 4567.")
    @Test fun codeBeatsOrderNumber() = assertCode("5821", "Order 99812345 confirmed. Your login code is 5821.")
    @Test fun codeBeatsReference() = assertCode("482913", "OTP=482913 (Ref: ABCD) do not share")

    // --- Other languages ---

    @Test fun spanish() = assertCode("482913", "Tu código de verificación es 482913. No lo compartas con nadie.")
    @Test fun portuguese() = assertCode("482913", "Seu código de verificação é 482913. Não compartilhe.")
    @Test fun french() = assertCode("482913", "Votre code de vérification est : 482913")
    @Test fun german() = assertCode("482913", "Ihr Bestätigungscode lautet: 482913")
    @Test fun germanTan() = assertCode("482913", "Ihre mTAN lautet 482913. Betrag: 50,00 EUR")
    @Test fun italian() = assertCode("482913", "Il tuo codice di verifica è 482913")
    @Test fun dutch() = assertCode("482913", "Je verificatiecode is 482913")
    @Test fun swedish() = assertCode("482913", "Din engångskod är 482913")
    @Test fun finnish() = assertCode("482913", "Vahvistuskoodisi on 482913")
    @Test fun polish() = assertCode("482913", "Twój kod weryfikacyjny to 482913")
    @Test fun czech() = assertCode("482913", "Váš ověřovací kód je 482913")
    @Test fun hungarian() = assertCode("482913", "Az ellenőrző kódod: 482913")
    @Test fun romanian() = assertCode("482913", "Codul tău de verificare este 482913")
    @Test fun turkish() = assertCode("482913", "Doğrulama kodunuz: 482913. Bu kodu kimseyle paylaşmayın.")
    @Test fun turkishSifre() = assertCode("482913", "Tek kullanımlık şifreniz 482913'dir.")
    @Test fun russian() = assertCode("4829", "Код подтверждения: 4829. Никому не сообщайте код.")
    @Test fun ukrainian() = assertCode("482913", "Ваш код підтвердження: 482913")
    @Test fun greek() = assertCode("482913", "Ο κωδικός επαλήθευσης είναι 482913")
    @Test fun arabicIndicDigits() = assertCode("482913", "رمز التحقق الخاص بك هو: ٤٨٢٩١٣")
    @Test fun persianDigits() = assertCode("482913", "کد تایید شما: ۴۸۲۹۱۳")
    @Test fun hebrew() = assertCode("482913", "קוד האימות שלך הוא 482913")
    @Test fun hindi() = assertCode("482913", "आपका OTP 482913 है। इसे किसी के साथ साझा न करें।")
    @Test fun devanagariDigits() = assertCode("482913", "आपका सत्यापन कोड ४८२९१३ है")
    @Test fun bengali() = assertCode("482913", "আপনার যাচাইকরণ কোড ৪৮২৯১৩")
    @Test fun thai() = assertCode("482913", "รหัส OTP ของคุณคือ 482913 (Ref: ABCD)")
    @Test fun thaiDigits() = assertCode("482913", "รหัสยืนยันของคุณคือ ๔๘๒๙๑๓")
    @Test fun vietnamese() = assertCode("482913", "Mã xác thực của bạn là 482913")
    @Test fun indonesian() = assertCode("482913", "Kode verifikasi Anda adalah 482913. Jangan berikan kode ini kepada siapa pun.")
    @Test fun chineseSimplified() = assertCode("482913", "【京东】验证码：482913，用于登录，5分钟内有效，请勿泄露。")
    @Test fun chineseTraditional() = assertCode("482913", "您的驗證碼為482913，請勿告知他人。")
    @Test fun chineseFullWidthDigits() = assertCode("482913", "验证码：４８２９１３")
    @Test fun japanese() = assertCode("482913", "認証コード：482913 このコードを他人に教えないでください。")
    @Test fun korean() = assertCode("482913", "[Web발신]\n[네이버] 인증번호[482913]를 입력해 주세요.")
    @Test fun koreanParticle() = assertCode("482913", "인증번호는 482913입니다.")
    @Test fun burmese() = assertCode("482913", "သင်၏ အတည်ပြုကုဒ်မှာ ၄၈၂၉၁၃ ဖြစ်သည်")
    @Test fun invisibleCharacters() = assertCode("482913", "Your code is 48\u200B29\u200E13")

    // --- Messages that must not produce a code ---

    @Test fun noKeyword() = assertNoCode("Meeting at 1530 in room 4012")
    @Test fun packageArrival() = assertNoCode("Your package 482913 will arrive tomorrow")
    @Test fun orderConfirmed() = assertNoCode("Your order #123456 has been confirmed")
    @Test fun balance() = assertNoCode("Your a/c XX1234 debited INR 500.00 on 12-05-24. Avl bal INR 10,000.00. Call 18001234567")
    @Test fun promoAlphanumeric() = assertNoCode("Use code SAVE20 for 20% off your next order!")
    @Test fun promoDigits() = assertNoCode("Use code 1234 to get 10% off your first ride")
    @Test fun priceWithCode() = assertNoCode("Zip code 10001 delivery fee is $4.99")
    @Test fun chat() = assertNoCode("See you at 2030, bring the 4 tickets", title = "Alex")
    @Test fun yearOnly() = assertNoCode("Happy new year 2025!")
    @Test fun passwordChanged() = assertNoCode("Your password was changed on 12/05/2024 at 10:30")
    @Test fun phoneOnly() = assertNoCode("Call me back on +44 7700 900123", title = "Mum")
    @Test fun maskedCard() = assertNoCode("Payment of $25.00 approved on card ••••4821")
    @Test fun emptyText() = assertNoCode("")

    // --- Harder mixed cases ---

    @Test fun brandBetween() = assertCode("482913", "Your code for Google is 482913")
    @Test fun dashSeparatedPhrase() = assertCode("482913", "482913 - your Facebook confirmation code")
    @Test fun referenceAfterCode() = assertCode("482913", "Your OTP is 482913. Ref no 77881234.")
    @Test fun timeAfterCode() = assertCode("482913", "Hi! Your code is 482913 and expires at 10:45.", title = "Verification code")
    @Test fun weakPin() = assertCode("4829", "Your PIN is 4829")
    @Test fun spanishClave() = assertCode("4829", "Tu clave de acceso es 4829")
    @Test fun kakao() = assertCode("482913", "[카카오] 인증번호 482913 타인에게 절대 알려주지 마세요")
    @Test fun line() = assertCode("482913", "【LINE】認証番号：482913")
    @Test fun urlTokenIgnored() = assertCode("482913", "Tap to sign in: https://example.com/login?token=48291374 or use code: 482913")
    @Test fun emailAddressIgnored() = assertCode("5521", "Sign in to user482913@gmail.com with code 5521")
    @Test fun indianSenderHeader() = assertCode("482913", "Your Amazon OTP is 482913. Do not share it.", title = "AX-AMAZON")
    @Test fun expiresMinutes() = assertCode("482913", "Code: 482913 (valid for 10 min)")
    @Test fun bidiMarks() = assertCode("482913", "\u200Fرمز التحقق: \u200E٤٨٢٩١٣\u200E")
    @Test fun netflixEmail() = assertCode(
        "4829",
        "Netflix — Your sign-in code — Enter this code to sign in: 4829. This code will expire in 15 minutes.",
        title = "Netflix",
    )

    @Test fun codeFarIntoEmail() = assertCode(
        "482913",
        "Hello,\n" + "We noticed a new sign-in to your account from a Windows device. ".repeat(12) +
            "\nYour verification code:\n482913\n\nIf this was not you, secure your account.",
    )

    @Test fun flightNumber() = assertNoCode("Flight TK1234 departs at 14:30 from gate 12")
    @Test fun verificationButOrder() = assertNoCode("Your verification is complete. Order 48291 ships today.")
    @Test fun callToVerify() = assertNoCode("Call 555-1234 to verify your account")
    @Test fun dateWithVerification() = assertNoCode("Identity verification scheduled for 12-05-2024")
    @Test fun storage() = assertNoCode("Security update: 2048 MB free, 4096 MB used")
    @Test fun chatNumber() = assertNoCode("lol 4829", title = "Bob")

    @Test fun extractionIsFast() {
        val long = ("We noticed a new sign-in attempt from order 12345678 at $19.99. ".repeat(60) +
            "Your verification code is 482913").take(4_000)
        LoginCodeExtractor.extract(long)
        val started = System.nanoTime()
        repeat(200) { LoginCodeExtractor.extract(long) }
        val perCallMs = (System.nanoTime() - started) / 200 / 1_000_000.0
        assert(perCallMs < 20.0) { "extract took $perCallMs ms per call" }
    }
}
