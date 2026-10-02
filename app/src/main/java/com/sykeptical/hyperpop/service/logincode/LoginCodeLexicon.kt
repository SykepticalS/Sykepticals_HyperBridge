package com.sykeptical.hyperpop.service.logincode

/**
 * Multilingual vocabulary for [LoginCodeExtractor]. Terms are matched case-insensitively after
 * the same normalization applied to notification text. See [TermIndex] for the `*` syntax.
 */
internal object LoginCodeLexicon {
    const val STRONG = 3
    const val MEDIUM = 2
    const val WEAK = 1

    /** Nouns that name the code itself. */
    private val strong = listOf(
        // English
        "code", "codes", "*code", "*codes", "otp", "otps", "one-time password", "one time password",
        "one-time passcode", "one time passcode", "one-time code", "one time code", "passcode",
        "pass code", "2fa", "mfa", "tan", "mtan", "smstan", "sms-tan", "pushtan", "<#>",
        // Spanish, Portuguese, Galician, Catalan, Italian, Romanian
        "código*", "codigo*", "codi", "codice", "codici", "cod", "codul", "codului",
        // German, Dutch, Nordic
        "einmalpasswort", "einmal-passwort", "einmalkennwort", "*kode", "*koden", "*koder", "*kod",
        "*koodi", "*koodin", "*koodia", "tunnusluku",
        // Slavic, Baltic, Hungarian, Turkish, Albanian, Icelandic
        "kod*", "kód*", "kôd", "kodas", "kodą", "kods", "kood", "koodi", "kodi", "kodin", "kóði",
        "hasło jednorazowe", "jednorazow*", "tek kullanımlık",
        // Cyrillic
        "код", "кода", "коду", "кодом", "коде", "одноразов*",
        // Greek, Georgian, Armenian
        "κωδικ*", "κωδ.", "კოდი", "კოდს", "կոդ", "կոդը",
        // Arabic, Persian, Urdu, Hebrew
        "*رمز*", "*كود*", "*الرمز*", "کد", "کدی", "*کوڈ*", "*קוד*",
        // Indic
        "कोड", "ओटीपी", "কোড", "ওটিপি", "குறியீடு", "కోడ్", "ಕೋಡ್", "കോഡ്", "કોડ", "ਕੋਡ",
        // South-East Asian
        "รหัส", "mã", "kode", "ကုဒ်", "លេខកូដ", "ລະຫັດ",
        // Chinese
        "验证码", "驗證碼", "校验码", "校驗碼", "动态码", "動態碼", "确认码", "確認碼", "登录码", "登錄碼",
        "安全码", "安全碼", "认证码", "認證碼", "授权码", "授權碼", "动态密码", "動態密碼", "验证代码", "驗證代碼",
        "短信码", "简讯码", "簡訊碼", "口令",
        // Japanese
        "認証コード", "確認コード", "検証コード", "認証番号", "確認番号", "ワンタイムパスワード", "ワンタイム",
        "パスコード", "セキュリティコード", "コード", "暗証番号",
        // Korean
        "인증번호", "인증 번호", "인증코드", "인증 코드", "확인코드", "확인 코드", "확인번호", "보안코드",
        "승인번호", "코드", "일회용",
    )

    /** Context that marks a message as an authentication step. */
    private val medium = listOf(
        // English
        "verif*", "authenticat*", "authoriz*", "authoris*", "confirm*", "login", "log in", "log-in",
        "logon", "sign in", "sign-in", "signin", "sign on", "security", "secure", "two-factor",
        "two factor", "2-step", "2 step", "one-time", "one time", "expire*", "valid for", "valid until",
        "do not share", "don't share", "dont share", "never share", "not share", "do not disclose",
        "don't disclose", "token", "identity", "access",
        // Spanish, Portuguese, Galician, Catalan
        "autentic*", "confirmación", "confirmacion", "inicio de sesión", "iniciar sesión",
        "no compartas", "no lo compartas", "no la compartas", "caduca", "válido", "valido", "acceso",
        "clave", "confirmação", "não compartilhe", "nao compartilhe", "não partilhe", "acesso", "senha",
        "expira", "verificació", "inici de sessió", "contrasenya",
        // French
        "vérif*", "authentification", "connexion", "sécurité", "ne partagez", "ne le partagez",
        "ne communiquez", "valable",
        // German
        "bestätig*", "bestaetig*", "verifizier*", "anmeld*", "authentifizier*", "sicherheit*",
        "gültig", "gueltig", "nicht weiter", "niemals weiter", "geben sie", "zugang*",
        // Italian
        "conferm*", "accesso", "autenticazione", "sicurezza", "non condividere", "scade",
        // Dutch
        "verificatie", "bevestig*", "inloggen", "aanmelden", "beveiliging*", "niet delen", "geldig",
        "eenmalig*",
        // Nordic
        "bekräfta*", "bekreft*", "bekræft*", "logga in", "logg inn", "log ind", "vahvist*", "kirjaudu*",
        "kirjautu*", "älä jaa", "del ikke", "dela inte", "engångs*", "engangs*",
        // Polish, Czech, Slovak, Slovene, Croatian, Serbian
        "weryfikac*", "potwierdz*", "logowani*", "zaloguj", "uwierzytelni*", "nie udostępniaj",
        "ověřovací", "ověření", "přihlášení", "potvrzení", "overovací", "overenie", "prihlásenie",
        "potvrdenie", "potrditev", "prijava", "potvrda", "verifikacij*", "prijav*",
        // Hungarian, Romanian
        "ellenőrző", "megerősít*", "bejelentkez*", "hitelesít*", "érvényes", "verificare",
        "confirmare", "autentificare", "valabil",
        // Turkish, Azerbaijani
        "doğrulama", "dogrulama", "onay", "giriş", "giris", "güvenlik", "guvenlik", "şifre*", "sifre*",
        "paylaşmayın", "paylasmayin", "paylaşmayınız", "geçerli", "tesdiq", "təsdiq",
        // Cyrillic
        "подтвержд*", "проверочн*", "вход", "входа", "авториз*", "никому не", "не сообщайте",
        "действител*", "підтвердж*", "перевір*", "вхід", "входу", "нікому не", "не повідомляйте",
        "потвърд*", "верификац*",
        // Greek
        "επαλήθευσ*", "επιβεβαίωσ*", "σύνδεσ*", "ασφαλείας",
        // Arabic, Persian, Hebrew
        "*التحقق*", "*تحقق*", "*التأكيد*", "*تأكيد*", "*تسجيل الدخول*", "*لا تشارك*", "*كلمة المرور*",
        "تایید", "تأیید", "ورود", "یکبار مصرف", "*אימות*", "*כניסה*", "*התחברות*",
        // Indic
        "सत्यापन", "पुष्टि", "लॉगिन", "साझा न करें", "যাচাই", "লগইন", "சரிபார்ப்பு",
        // South-East Asian
        "ยืนยัน", "เข้าสู่ระบบ", "ห้ามเปิดเผย", "xác minh", "xác thực", "xác nhận", "đăng nhập",
        "không chia sẻ", "hiệu lực", "verifikasi", "konfirmasi", "jangan berikan", "jangan bagikan",
        "rahasiakan", "berlaku", "sandi", "pengesahan", "အတည်ပြု",
        // Chinese
        "验证", "驗證", "登录", "登錄", "登陆", "登入", "认证", "認證", "确认", "確認", "有效期", "请勿",
        "請勿", "泄露", "洩露", "切勿", "转告", "轉告",
        // Japanese
        "認証", "ログイン", "本人確認", "有効期限", "他人に",
        // Korean
        "인증", "로그인", "본인확인", "유효", "타인에게",
    )

    /** Secrets that sometimes, but not always, travel as a code. */
    private val weak = listOf(
        "pin", "password", "passwort", "kennwort", "contraseña", "contrasena", "mot de passe",
        "wachtwoord", "hasło", "haslo", "heslo", "jelszó", "parola", "parolă", "пароль", "лозинка",
        "密码", "密碼", "パスワード", "비밀번호", "mật khẩu", "kata sandi", "סיסמ*", "पासवर्ड",
        "*رمز عبور*", "รหัสผ่าน", "salasana", "lösenord", "passord", "adgangskode",
    )

    val keywords = TermIndex(
        strong.map { it to STRONG } + medium.map { it to MEDIUM } + weak.map { it to WEAK }
    )

    /**
     * Words that name a different number (order, account, phone, money). A nearer negative than
     * any keyword pulls a candidate down.
     */
    val negatives = TermIndex(
        listOf(
            // English
            "order" to 28, "order no" to 30, "order number" to 30, "tracking" to 30, "track" to 22,
            "shipment" to 26, "parcel" to 22, "package" to 18, "invoice" to 30, "receipt" to 26,
            "ref" to 26, "ref." to 26, "reference" to 28, "booking" to 24, "reservation" to 24,
            "pnr" to 26, "flight" to 22, "seat" to 22, "gate" to 18, "account" to 12, "acct" to 22,
            "a/c" to 26, "card" to 24, "ending" to 30, "ends with" to 30, "ending in" to 32,
            "last 4" to 32, "balance" to 30, "bal" to 26, "amount" to 30, "total" to 26,
            "paid" to 22, "payment of" to 26, "debited" to 30, "credited" to 30, "spent" to 24,
            "withdraw*" to 26, "deposit*" to 26, "txn" to 16, "transaction" to 14, "id" to 12,
            "no." to 22, "no:" to 22, "nr" to 18, "nr." to 20, "number" to 14, "#" to 10, "№" to 24,
            "call" to 28, "phone" to 28, "tel" to 28, "tel." to 28, "mobile" to 16, "contact" to 22,
            "helpline" to 30, "hotline" to 30, "customer care" to 30, "fax" to 28, "zip" to 26,
            "postcode" to 30, "postal code" to 30, "zip code" to 30, "barcode" to 26,
            "address" to 20, "street" to 24, "floor" to 20, "room" to 20, "ticket" to 22,
            "case" to 16, "policy" to 20, "claim" to 20, "serial" to 24, "model" to 24,
            "version" to 26, "build" to 20, "item" to 18, "sku" to 26, "qty" to 26, "price" to 26,
            "fee" to 22, "bill" to 24, "due" to 16, "points" to 24, "miles" to 22, "steps" to 22,
            "customer id" to 24, "user id" to 20, "member" to 16, "employee" to 20,
            // Spanish, Portuguese, Catalan
            "pedido" to 28, "orden" to 20, "factura" to 28, "cuenta" to 14, "tarjeta" to 24,
            "saldo" to 30, "importe" to 28, "monto" to 28, "referencia" to 28, "llama" to 24,
            "llámanos" to 26, "teléfono" to 28, "envío" to 22, "seguimiento" to 28, "fatura" to 28,
            "conta" to 12, "cartão" to 24, "valor" to 22, "referência" to 28, "telefone" to 28,
            "rastreio" to 28, "encomenda" to 26,
            // French
            "commande" to 28, "facture" to 28, "compte" to 12, "carte" to 24, "solde" to 30,
            "montant" to 28, "référence" to 28, "appelez" to 26, "téléphone" to 28, "suivi" to 26,
            "colis" to 24,
            // German
            "bestellung" to 28, "bestellnummer" to 30, "rechnung" to 28, "konto" to 12, "karte" to 22,
            "betrag" to 28, "kontostand" to 30, "referenz" to 26, "sendung" to 26, "anrufen" to 26,
            "telefon" to 26, "kundennummer" to 30, "auftrag" to 22, "paket" to 22,
            // Italian, Dutch
            "ordine" to 28, "fattura" to 28, "conto" to 12, "carta" to 22, "importo" to 28,
            "riferimento" to 28, "telefono" to 26, "spedizione" to 24, "bestelling" to 28,
            "factuur" to 28, "rekening" to 14, "bedrag" to 28, "referentie" to 26, "zending" to 24,
            "bel ons" to 26,
            // Polish, Czech, Slovak, Hungarian, Romanian
            "zamówieni*" to 28, "faktura" to 28, "karta" to 22, "kwota" to 28, "przesyłka" to 24,
            "zadzwoń" to 26, "objednávk*" to 28, "částka" to 28, "zásilk*" to 24, "rendelés" to 28,
            "összeg" to 28, "comanda" to 28, "comandă" to 28, "suma" to 24,
            // Turkish
            "sipariş*" to 28, "siparis*" to 28, "fatura" to 28, "hesap" to 12, "kart" to 22,
            "tutar" to 28, "bakiye" to 30, "referans" to 26, "kargo" to 26, "takip" to 26,
            "arayın" to 26, "arayin" to 26, "numara*" to 12,
            // Cyrillic
            "заказ*" to 28, "счет" to 20, "счёт" to 20, "карт*" to 22, "сумма" to 28, "сумму" to 28,
            "баланс" to 30, "остаток" to 28, "номер" to 12, "трек" to 28, "звоните" to 26,
            "телефон*" to 26, "замовлення" to 28, "рахунок" to 20, "картк*" to 22, "сума" to 28,
            "посилк*" to 24, "посылк*" to 24,
            // Arabic, Persian, Hebrew
            "*رصيد*" to 30, "*مبلغ*" to 28, "*بطاقة*" to 22, "*طلبك*" to 24, "*فاتورة*" to 28,
            "*هاتف*" to 24, "*اتصل*" to 24, "*شحنة*" to 24, "سفارش" to 28, "مبلغ" to 28,
            "موجودی" to 30, "*הזמנה*" to 28, "*יתרה*" to 30, "*סכום*" to 28,
            // Indic
            "ऑर्डर" to 28, "खाता" to 12, "राशि" to 28, "कार्ड" to 22,
            // South-East Asian
            "อ้างอิง" to 30, "คำสั่งซื้อ" to 28, "บัญชี" to 12, "ยอด" to 26, "บาท" to 30,
            "đơn hàng" to 28, "mã đơn" to 30, "số dư" to 30, "số tiền" to 28, "tài khoản" to 12,
            "pesanan" to 28, "tagihan" to 28, "rekening" to 14, "jumlah" to 24, "resi" to 28,
            // Chinese
            "号码" to 26, "號碼" to 26, "电话" to 28, "電話" to 28, "订单" to 28, "訂單" to 28,
            "单号" to 30, "單號" to 30, "尾号" to 34, "尾號" to 34, "卡号" to 30, "卡號" to 30,
            "金额" to 30, "金額" to 30, "余额" to 30, "餘額" to 30, "快递" to 26, "快遞" to 26,
            "运单" to 28, "運單" to 28, "客服" to 26,
            // Japanese
            "注文" to 28, "お問い合わせ" to 26, "口座" to 14, "残高" to 30, "金額" to 30, "配送" to 24,
            "追跡" to 28, "伝票" to 28,
            // Korean
            "주문" to 28, "계좌" to 16, "잔액" to 30, "금액" to 30, "배송" to 24, "송장" to 30,
            "전화" to 28, "문의" to 22, "카드" to 22,
        )
    )

    /** Marketing vocabulary. Without verification context these messages carry promo codes. */
    val promo = TermIndex(
        listOf(
            "% off", "off your", "discount", "coupon", "promo", "promo code", "voucher", "sale",
            "deal", "deals", "offer", "cashback", "free shipping", "gift", "save up to", "reward",
            "rewards", "loyalty", "descuento", "cupón", "cupon", "oferta", "rebaja", "desconto",
            "réduction", "remise", "soldes", "rabatt", "gutschein", "angebot", "sconto", "buono",
            "korting", "aanbieding", "zniżka", "rabat", "kupon", "indirim", "fırsat", "firsat",
            "kampanya", "скидк*", "промокод", "акци*", "знижк*", "خصم", "تخفيف", "تخفیف", "הנחה",
            "छूट", "ส่วนลด", "giảm giá", "khuyến mãi", "diskon", "promosi", "优惠", "優惠", "折扣",
            "红包", "紅包", "割引", "クーポン", "セール", "할인", "쿠폰", "이벤트",
        ).map { it to 1 }
    )

    /**
     * Words that may sit between a term and its code without breaking the link:
     * copulas, possessives, articles and prepositions ("code *is* 1234", "1234 *is your* code").
     */
    val fillers: Set<String> = hashSetOf(
        // English
        "is", "was", "be", "will", "are", "your", "you", "yours", "the", "a", "an", "for", "as", "use",
        "enter", "type", "here", "below", "following", "this", "that", "s", "new", "now", "to", "of",
        "account", "app", "please", "just", "requested", "one", "time",
        // Romance
        "es", "est", "está", "esta", "este", "és", "é", "è", "e", "son", "são", "sont", "tu", "su", "sus",
        "seu", "sua", "votre", "vos", "ton", "ta", "il", "la", "le", "lo", "el", "los", "las", "les",
        "de", "del", "do", "da", "du", "des", "para", "pour", "per", "por", "como", "come", "comme",
        "usa", "utiliza", "utilice", "usa", "ingresa", "ingrese", "introduce", "introduzca", "digite",
        "insira", "utilize", "utilisez", "saisissez", "entrez", "inserisci", "ce", "ceci", "questo",
        "questa", "ecco", "voici", "aqui", "aquí", "ton", "teu", "tua", "seu", "nuevo", "novo",
        "nouveau", "nuovo", "este", "acest", "codul", "tău", "dvs", "este:",
        // Germanic
        "ist", "lautet", "sind", "ihr", "ihre", "dein", "deine", "der", "die", "das", "für", "fur",
        "als", "hier", "neuer", "neue", "uw", "je", "jouw", "voor", "hier", "is:", "er", "är", "din",
        "ditt", "dit", "din", "för", "til", "till", "on", "sinun", "teidän",
        // Slavic and others
        "jest", "to", "twój", "twoj", "twoja", "twoje", "váš", "vaš", "vaše", "tvůj", "je", "sú",
        "dla", "pro", "za", "sizin", "senin", "için", "icin", "kodunuz", "ваш", "ваша", "ваше",
        "твой", "твій", "ваш", "для", "это", "є", "будет", "είναι", "σας", "ο", "η", "το",
        // Arabic, Persian, Hebrew
        "هو", "هي", "الخاص", "بك", "لك", "الخاص", "شما", "است", "شما:", "שלך", "הוא", "היא",
        // Indic
        "है", "हैं", "आपका", "का", "की", "के", "आपकी", "এর", "আপনার", "হল", "হলো",
        // South-East Asian
        "của", "bạn", "là", "anda", "kamu", "adalah", "ialah", "ini", "untuk", "ของคุณ", "คือ",
        "สำหรับ", "ของ",
        // CJK
        "是", "为", "為", "您的", "你的", "您", "你", "的", "本次", "は", "です", "の", "こちら",
        "는", "은", "이", "입니다", "귀하의",
    )

    /** Imperatives that directly precede a code ("Use 1234 as ...", "Enter 1234"). */
    val leads: Set<String> = hashSetOf(
        "use", "enter", "type", "input", "usa", "use:", "utiliza", "utilice", "usar", "introduce",
        "introduzca", "ingresa", "ingrese", "digite", "insira", "utilize", "utilisez", "saisissez",
        "entrez", "gib", "geben", "verwenden", "eingeben", "inserisci", "utilizza", "voer", "gebruik",
        "wpisz", "użyj", "uzyj", "zadejte", "zadajte", "použijte", "girin", "kullanın", "kullanin",
        "введите", "используйте", "введіть", "використовуйте", "masukkan", "gunakan", "nhập",
        "dùng", "sử", "ange", "skriv", "angiv", "oppgi", "syötä", "adja", "használja", "introduceți",
        "folosiți", "εισάγετε", "χρησιμοποιήστε", "أدخل", "استخدم", "وارد", "הזן", "दर्ज", "输入",
        "輸入", "填写", "填寫", "入力", "입력",
    )

    /** Letter-only runs that are never codes. */
    val letterStopWords: Set<String> = hashSetOf(
        "CODE", "CODES", "OTP", "SMS", "PIN", "TAN", "HTTP", "HTTPS", "NULL", "TRUE", "FALSE", "NOTE",
        "INFO", "HELP", "STOP", "LINK", "WARNING", "ALERT", "URGENT", "BANK", "DEAR", "USER", "LOGIN",
        "VERIFY", "SECURITY", "GOOGLE", "APPLE", "AMAZON", "STEAM", "PAYPAL", "UBER", "MICROSOFT",
        "WHATSAPP", "TELEGRAM", "INSTAGRAM", "FACEBOOK", "TWITTER", "TIKTOK", "NETFLIX", "SPOTIFY",
        "XIAOMI", "SAMSUNG", "HUAWEI", "ACCOUNT", "EMAIL", "MAIL", "NEVER", "SHARE", "THIS", "YOUR",
        "WITH", "FROM", "THAT", "HAVE", "WILL", "VALID", "MINUTES", "ONLY", "ANYONE", "REPLY", "CALL",
        "FREE", "OFFER", "SALE", "HERE", "CLICK", "OPEN", "SIGN", "CONFIRM", "GUARD", "DONT", "PLEASE",
        "THANK", "THANKS", "WELCOME", "HELLO", "IGNORE", "SUPPORT", "TEAM", "BANKING", "CARD",
    )

    val unitSuffixes: Set<String> = hashSetOf(
        "GB", "MB", "KB", "TB", "MP", "FPS", "HZ", "KHZ", "MHZ", "GHZ", "KM", "KG", "MM", "CM", "ML",
        "MAH", "KW", "KWH", "W", "V", "PM", "AM", "TL", "USD", "EUR", "GBP", "TRY", "RUB", "INR", "JPY",
        "CNY", "RMB", "KRW", "BRL", "MXN", "AED", "SAR", "EGP", "PLN", "CZK", "HUF", "RON", "SEK",
        "NOK", "DKK", "CHF", "IDR", "MYR", "THB", "VND", "PHP", "NGN", "UAH", "K", "M", "G", "P", "X",
        "D", "H", "S", "MIN", "MINS", "HR", "HRS", "ST", "ND", "RD", "TH",
    )

    const val currencySymbols = "$€£¥₹₽₺₩₪₫฿₱₦₴₸₼₾¢﷼₲₵₡₭₮₨"

    val currencyWords: Set<String> = hashSetOf(
        "usd", "eur", "gbp", "try", "tl", "rub", "inr", "rs", "rs.", "jpy", "cny", "rmb", "krw", "brl",
        "r$", "mxn", "aed", "sar", "egp", "pln", "zł", "zl", "kč", "czk", "huf", "ft", "ron", "lei",
        "sek", "nok", "dkk", "kr", "kr.", "chf", "idr", "rp", "rp.", "myr", "rm", "thb", "vnd", "đ",
        "php", "ngn", "uah", "грн", "руб", "руб.", "р.", "лв", "ден", "元", "円", "원", "دينار",
        "درهم", "ريال", "جنيه", "تومان", "ریال", "₪", "ש\"ח", "टका", "রুপি", "บาท", "đồng", "dollars",
        "dollar", "euro", "euros", "pesos", "reais", "lira", "rupees", "yuan", "yen", "won",
    )

    /** Units that follow quantities, never codes. CJK/Hangul entries match without a boundary. */
    val units: List<String> = listOf(
        "%", "km", "m", "kg", "g", "mb", "gb", "kb", "tb", "mah", "w", "kw", "kwh", "mph", "kmh",
        "km/h", "fps", "px", "ms", "min", "mins", "minute", "minutes", "sec", "secs", "second",
        "seconds", "hr", "hrs", "hour", "hours", "day", "days", "dk", "dakika", "saat", "gün", "gun",
        "мин", "минут", "сек", "час", "часа", "дней", "дня", "minuten", "stunden", "tage", "minutos",
        "horas", "días", "dias", "minuti", "ore", "giorni", "points", "pts", "steps", "calories",
        "kcal", "items", "x", "people", "persons", "likes", "views", "followers", "messages",
        "分钟", "分鐘", "秒", "小时", "小時", "天", "分", "時間", "日", "个", "個", "件", "人", "条",
        "條", "분", "초", "시간", "일", "명", "개", "건", "دقيقة", "دقائق", "ساعة", "دقیقه", "นาที",
        "phút", "menit", "मिनट", "মিনিট",
    )
}
