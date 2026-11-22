package com.sketchware.ai.github;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v2.1.2 (owner's 21 Nov directive "in-app email OTP quick-assist bar, no security bypass"): GitHub's own verification
 * pages inside the sign-in window (the e-mailed device code at {@code /sessions/verified-device}, a two-factor code at
 * {@code /sessions/two-factor…}) get a bar with "Open Gmail" (the code is in GitHub's e-mail) and "Paste & fill" (a 6- or
 * 8-digit code the user copied goes into GitHub's own field on that page, then GitHub's own Verify button is pressed).
 * Nothing is read from the e-mail and nothing is skipped: the user brings the code, GitHub checks it; the token stays
 * Keystore-bound and the one-phone rule is unchanged. Only https github.com pages at those paths, only digits.
 */
public final class OtpAssist {

    /** The first standalone 8- or 6-digit code (also "123 456" / "123-456"). */
    private static final Pattern CODE = Pattern.compile("(?<![0-9])([0-9]{8}|[0-9]{6})(?![0-9])");
    private static final Pattern SPLIT = Pattern.compile("(?<![0-9])([0-9]{3,4})[ -]([0-9]{3,4})(?![0-9])");

    private OtpAssist() {
    }

    /**
     * "email" on GitHub's e-mailed device code page, "two-factor" on a two-factor code page, "login" on GitHub's other
     * sign-in pages (v2.1.3, owner's 22 Nov report "the Gmail code helper didn't show": the bar is there from the first
     * sign-in page on, for the code GitHub may e-mail after the password), else null (the device-code page, which the
     * window fills by itself, passkey and recovery pages, any other site).
     */
    public static String pageKind(String scheme, String host, String path) {
        if (!"https".equals(scheme) || !"github.com".equals(host) || path == null) return null;
        if (path.equals("/sessions/verified-device") || path.startsWith("/sessions/verified-device/")) return "email";
        if (path.startsWith("/sessions/two-factor")) {
            return path.contains("webauthn") || path.contains("recovery") ? null : "two-factor";
        }
        if (path.startsWith("/login/device") || path.startsWith("/login/oauth")) return null;
        if (path.equals("/login") || path.startsWith("/login/") || path.equals("/session") || path.startsWith("/sessions")) {
            return path.contains("webauthn") || path.contains("recovery") ? null : "login";
        }
        return null;
    }

    /** The code in what the user copied (a code alone, or the e-mail's text), else null. */
    public static String codeFrom(CharSequence copied) {
        if (copied == null) return null;
        String text = copied.toString();
        if (text.length() > 20_000) text = text.substring(0, 20_000);
        Matcher m = CODE.matcher(text);
        if (m.find()) return m.group(1);
        Matcher split = SPLIT.matcher(text);
        while (split.find()) {
            String joined = split.group(1) + split.group(2);
            if (joined.length() == 6 || joined.length() == 8) return joined;
        }
        return null;
    }

    /**
     * v2.1.4: the page's code field, read from the page itself (one field or one box per digit). GitHub's code pages
     * come at many addresses (the "Confirm access" page before an authorization, the e-mailed code after the password,
     * two-factor): a field counts when the page marks it as a one-time code, names it a code / otp / totp / sudo /
     * verification field, shows "XXXXXXXX" or "000000" in it, or takes 6 or 8 digits. Never the device-code boxes the
     * window fills itself ({@code user-code-*}), the user name, password, e-mail or search fields.
     */
    static final String FIELD_JS = "function shown(e){for(var n=e;n&&n.nodeType===1;n=n.parentElement){"
            + "if(n.hidden||n.getAttribute('aria-hidden')==='true')return false;"
            + "if(window.getComputedStyle){var s=getComputedStyle(n);if(s.display==='none'||s.visibility==='hidden')"
            + "return false;}}return true;}"
            + "function usable(i){var t=(i.getAttribute('type')||'text').toLowerCase();"
            + "return i&&!i.disabled&&!i.readOnly&&(t==='text'||t==='tel'||t==='number')&&shown(i);}"
            + "function named(i){return ((i.name||'')+' '+(i.id||'')).toLowerCase();}"
            + "function excluded(i){var n=named(i);return /user-code|user_code|password|search|query|login_field/.test(n)"
            + "||i.name==='login'||i.name==='email'||i.name==='q';}"
            + "function codeField(i){if(!usable(i)||excluded(i))return false;var n=named(i);"
            + "if((i.getAttribute('autocomplete')||'').toLowerCase()==='one-time-code')return true;"
            + "if(i.maxLength===1)return false;"
            + "if(/otp|totp|verification|sudo|code/.test(n))return true;"
            + "var ph=(i.getAttribute('placeholder')||'').replace(/[\\s-]/g,'');"
            + "if(/^(x{6}|x{8}|X{6}|X{8}|0{6}|0{8}|123456|12345678)$/.test(ph))return true;"
            + "return (i.getAttribute('inputmode')||'').toLowerCase()==='numeric'&&(i.maxLength===6||i.maxLength===8);}"
            + "function codeBoxes(){var ones=Array.prototype.slice.call(document.querySelectorAll('input')).filter("
            + "function(i){return usable(i)&&!excluded(i)&&i.maxLength===1;});"
            + "return ones.length===6||ones.length===8?ones:null;}"
            + "function codeFields(){return Array.prototype.slice.call(document.querySelectorAll('input')).filter(codeField);}"
            + "function sendLinks(){var out=[];var els=document.querySelectorAll('a,button,input[type=submit],summary,"
            + "[role=button]');for(var k=0;k<els.length;k++){var e=els[k];"
            + "var tx=((e.innerText||e.textContent||e.value||'')+'').replace(/\\s+/g,' ').trim();"
            + "if(tx.length>80||!shown(e))continue;"
            + "if(/send (a |the |me a )?(new )?(verification |one-time |authentication |security |sign-in )?code"
            + "( via| by| to)?( your)? ?e-?mail/i.test(tx)||/e-?mail me a code/i.test(tx)){out.push(e);}}return out;}";

    /**
     * v2.1.4 (owner's 22 Nov report with three screenshots: no code bar on GitHub's "Confirm access" page with "Send a
     * code via email", nor on "Enter the verification code sent to b***@gmail.com"): what the page shows, read from the
     * page (GitHub shows these at many addresses): {@code "<code field 1|0>|<send-a-code-by-e-mail 1|0>|<the masked
     * address the code went to>|<confirm access 1|0>"}. Reads only; nothing is typed or pressed.
     */
    public static final String PROBE_SCRIPT = "(function(){" + FIELD_JS
            + "var code=codeFields().length>0||codeBoxes()!==null;var send=sendLinks().length>0;"
            + "var body=document.body?((document.body.innerText||document.body.textContent||'')+''):'';"
            + "var m=body.match(/[Ss]ent to:?\\s+([A-Za-z0-9._%+*\\u2022-]+@[A-Za-z0-9.*\\u2022-]+\\.[a-z]{2,24})/);"
            + "var h=document.querySelector('h1,h2');var head=(document.title||'')+' '+(h?(h.textContent||''):'');"
            + "var sudo=/confirm access|sudo/i.test(head);"
            + "return (code?'1':'0')+'|'+(send?'1':'0')+'|'+(m?m[1]:'')+'|'+(sudo?'1':'0');})()";

    /**
     * v2.1.4: presses the page's own "Send a code via email" (the user's tap on "E-mail me a code"): GitHub sends the
     * code to the account's e-mail. Answers "clicked" or "none".
     */
    public static final String SEND_SCRIPT = "(function(){" + FIELD_JS
            + "var l=sendLinks();if(!l.length)return 'none';l[0].click();return 'clicked';})()";

    /** v2.1.4: what {@link #PROBE_SCRIPT} found on the page. */
    public static final class Probe {
        public final boolean codeField;
        public final boolean sendLink;
        /** The masked address the page says the code went to ("b***@gmail.com"), else null. */
        public final String sentTo;
        public final boolean confirmAccess;

        Probe(boolean codeField, boolean sendLink, String sentTo, boolean confirmAccess) {
            this.codeField = codeField;
            this.sendLink = sendLink;
            this.sentTo = sentTo;
            this.confirmAccess = confirmAccess;
        }

        /** "1|0|b***@gmail.com|1" (evaluateJavascript's answer, already unquoted), else null. */
        public static Probe parse(String answer) {
            if (answer == null) return null;
            String[] p = answer.split("\\|", -1);
            if (p.length != 4) return null;
            String mail = p[2].trim();
            if (mail.length() > 120 || !mail.contains("@")) mail = null;
            return new Probe("1".equals(p[0]), "1".equals(p[1]), mail, "1".equals(p[3]));
        }

        /** The same page state (the bar is redrawn only when it changes). */
        String key() {
            return (codeField ? "c" : "-") + (sendLink ? "s" : "-") + (confirmAccess ? "a" : "-") + "|" + sentTo;
        }
    }

    /** v2.1.4: evaluateJavascript's answer (a JSON string literal) as plain text, else null. */
    public static String unquote(String json) {
        if (json == null || json.equals("null")) return null;
        try {
            return new org.json.JSONArray("[" + json + "]").getString(0);
        } catch (org.json.JSONException | RuntimeException e) {
            return null;
        }
    }

    /** "code" (6 or 8 digits) right after one of these words, in e-mails and their notifications. */
    private static final Pattern AFTER_WORD = Pattern.compile(
            "(?i)(code|verification|otp|passcode)[^0-9]{0,40}?(?<![0-9])([0-9]{8}|[0-9]{6})(?![0-9])");

    /**
     * v2.1.4 (owner: "the code sent to that e-mail should come into our app"): the code in an e-mail notification's
     * text, only when the text is GitHub's ("GitHub" in it), else null. A number after "code" / "verification" wins
     * over the first number (a date or an order number elsewhere in the text).
     */
    public static String codeFromGitHubMail(CharSequence text) {
        if (text == null) return null;
        String t = text.toString();
        if (t.length() > 20_000) t = t.substring(0, 20_000);
        if (!t.toLowerCase(java.util.Locale.ROOT).contains("github")) return null;
        Matcher m = AFTER_WORD.matcher(t);
        if (m.find()) return m.group(2);
        return codeFrom(t);
    }

    /**
     * The script that puts {@code code} (digits only) into the page's code field (one field, or one box per digit),
     * the way typing does (input / change events), then presses the form's own submit button unless the page sent the
     * form by itself. Answers "submitted", "filled" (no button to press) or "none" (no code field on the page).
     * v2.1.4: the field is found as {@link #FIELD_JS} finds it (GitHub's named fields first).
     */
    public static String fillScript(String code) {
        if (code == null || !code.matches("[0-9]{6}|[0-9]{8}")) throw new IllegalArgumentException("not a code");
        return "(function(code){" + FIELD_JS
                + "function put(i,v){var d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');"
                + "if(d&&d.set){d.set.call(i,v);}else{i.value=v;}"
                + "i.dispatchEvent(new Event('input',{bubbles:true}));i.dispatchEvent(new Event('change',{bubbles:true}));}"
                + "var sel='input[autocomplete=\"one-time-code\"],input#otp,input[name=\"otp\"],input#app_totp,"
                + "input[name=\"app_otp\"],input#sms_totp,input[name=\"sms_otp\"]';"
                + "var all=Array.prototype.slice.call(document.querySelectorAll(sel)).filter(function(i){"
                + "return usable(i)&&!excluded(i);}).concat(codeFields());"
                + "var target=null,boxes=null;"
                + "for(var k=0;k<all.length;k++){var ml=all[k].maxLength;if(!(ml>0&&ml<code.length)){target=all[k];break;}}"
                + "if(!target){var ones=codeBoxes();if(ones&&ones.length===code.length){boxes=ones;}}"
                + "if(!target&&!boxes)return 'none';"
                + "var first=target||boxes[0];var form=first.form||(first.closest?first.closest('form'):null);"
                + "var sent=false;if(form){form.addEventListener('submit',function(){sent=true;},true);}"
                + "if(target){target.focus();put(target,code);}else{for(var b=0;b<boxes.length;b++){put(boxes[b],code.charAt(b));}}"
                + "if(!form)return 'filled';"
                + "var button=form.querySelector('button[type=\"submit\"],input[type=\"submit\"]');"
                + "if(!button){var bs=form.querySelectorAll('button');for(var j=0;j<bs.length;j++){"
                + "if(!bs[j].getAttribute('type')){button=bs[j];break;}}}"
                + "setTimeout(function(){if(sent||!form.isConnected)return;"
                + "if(button&&!button.disabled){button.click();}"
                + "else if(form.requestSubmit){form.requestSubmit();}},500);"
                + "return 'submitted';"
                + "})('" + code + "')";
    }
}
