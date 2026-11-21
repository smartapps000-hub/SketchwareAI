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

    /** "email" on GitHub's e-mailed device code page, "two-factor" on a two-factor code page, else null. */
    public static String pageKind(String scheme, String host, String path) {
        if (!"https".equals(scheme) || !"github.com".equals(host) || path == null) return null;
        if (path.equals("/sessions/verified-device") || path.startsWith("/sessions/verified-device/")) return "email";
        if (path.startsWith("/sessions/two-factor") && !path.contains("webauthn") && !path.contains("recovery")) {
            return "two-factor";
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
     * The script that puts {@code code} (digits only) into the page's code field (one field, or one box per digit),
     * the way typing does (input / change events), then presses the form's own submit button unless the page sent the
     * form by itself. Answers "submitted", "filled" (no button to press) or "none" (no code field on the page).
     */
    public static String fillScript(String code) {
        if (code == null || !code.matches("[0-9]{6}|[0-9]{8}")) throw new IllegalArgumentException("not a code");
        return "(function(code){"
                + "function usable(i){return i&&!i.disabled&&!i.readOnly&&i.type!=='hidden'"
                + "&&(!window.getComputedStyle||getComputedStyle(i).display!=='none');}"
                + "function put(i,v){var d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');"
                + "if(d&&d.set){d.set.call(i,v);}else{i.value=v;}"
                + "i.dispatchEvent(new Event('input',{bubbles:true}));i.dispatchEvent(new Event('change',{bubbles:true}));}"
                + "var sel='input[autocomplete=\"one-time-code\"],input#otp,input[name=\"otp\"],input#app_totp,"
                + "input[name=\"app_otp\"],input#sms_totp,input[name=\"sms_otp\"]';"
                + "var all=Array.prototype.slice.call(document.querySelectorAll(sel)).filter(usable);"
                + "var target=null,boxes=null;"
                + "for(var k=0;k<all.length;k++){var ml=all[k].maxLength;if(!(ml>0&&ml<code.length)){target=all[k];break;}}"
                + "if(!target){var ones=Array.prototype.slice.call(document.querySelectorAll('input')).filter(function(i){"
                + "return usable(i)&&i.maxLength===1&&(i.type==='text'||i.type==='tel'||i.type==='number');});"
                + "if(ones.length===code.length){boxes=ones;}}"
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
