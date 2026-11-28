// v2.1.2: runs OtpAssist's fill script (printed by OtpScriptPrint) in jsdom against stand-ins of GitHub's verification
// pages: the e-mailed device code (one field + Verify), a page that sends the form by itself on input, six one-digit
// boxes, a two-factor app code field, a hidden / disabled field, no field. GitHub's real pages can't be opened from the
// build machine; this checks the script's logic and syntax only.
// v2.1.4: also the page reader (PROBE_SCRIPT) and the "Send a code via email" press (SEND_SCRIPT) against stand-ins of
// GitHub's "Confirm access" page (choices, "Send a code via email"), the e-mailed sudo code page ("Enter the
// verification code sent to b***@gmail.com", XXXXXXXX field), the device-code page (never a code page) and the sign-in
// page.
// Usage: node otp-script-test.js <script for 123456> <script for 12345678> [<probe script> <send script>]  (needs jsdom)
const fs = require('fs');
const { JSDOM } = require('jsdom');
const six = fs.readFileSync(process.argv[2], 'utf8');
const eight = fs.readFileSync(process.argv[3], 'utf8');
const probe = process.argv[4] ? fs.readFileSync(process.argv[4], 'utf8') : null;
const send = process.argv[5] ? fs.readFileSync(process.argv[5], 'utf8') : null;
let ok = 0, total = 0;
function check(name, cond, detail) {
  total++; if (cond) ok++;
  console.log('OTP CHECK ' + (cond ? 'ok  ' : 'FAIL') + ' ' + name + (detail === undefined ? '' : ' - ' + detail));
}
function page(url, body, autoSubmit) {
  const dom = new JSDOM('<!doctype html><html><body>' + body + '</body></html>', { url, runScripts: 'outside-only' });
  const w = dom.window;
  w.submits = 0;
  w.inputs = 0;
  w.document.addEventListener('input', () => w.inputs++, true);
  w.document.addEventListener('submit', e => { w.submits++; e.preventDefault(); }, false);
  if (autoSubmit) {
    w.document.addEventListener('input', e => {
      if (e.target.value && e.target.value.length === 6 && e.target.form) e.target.form.requestSubmit();
    }, false);
  }
  return w;
}
const wait = ms => new Promise(r => setTimeout(r, ms));
(async () => {
  const device = '<header><input type="text" name="q" placeholder="Search"></header>'
    + '<form action="/sessions/verified-device" method="post"><input type="hidden" name="authenticity_token" value="x">'
    + '<input type="text" id="otp" name="otp" autocomplete="one-time-code" maxlength="6">'
    + '<button type="submit">Verify</button></form>';
  let w = page('https://github.com/sessions/verified-device', device);
  let r = w.eval(six);
  check('e-mailed code page: the code in GitHub\'s field', r === 'submitted' && w.document.querySelector('#otp').value === '123456', r);
  check('input / change events like typing', w.inputs === 1, w.inputs);
  check('header search box left alone', w.document.querySelector('input[name=q]').value === '');
  check('Verify not pressed at once (the page may send it by itself)', w.submits === 0, w.submits);
  await wait(700);
  check('Verify pressed once after half a second', w.submits === 1, w.submits);

  w = page('https://github.com/sessions/verified-device', device, true);
  r = w.eval(six);
  await wait(700);
  check('a page that sends the form by itself on input: sent once, not twice', w.submits === 1, w.submits);

  const boxes = '<form action="/sessions/verified-device" method="post">'
    + [0, 1, 2, 3, 4, 5].map(i => '<input type="tel" maxlength="1" name="d' + i + '">').join('')
    + '<button>Verify</button></form>';
  w = page('https://github.com/sessions/verified-device', boxes);
  r = w.eval(six);
  const got = Array.from(w.document.querySelectorAll('input[maxlength="1"]')).map(i => i.value).join('');
  await wait(700);
  check('six one-digit boxes: filled in order, sent', r === 'submitted' && got === '123456' && w.submits === 1, r + ' ' + got + ' ' + w.submits);

  const totp = '<form action="/sessions/two-factor" method="post"><input type="text" id="app_totp" name="app_otp" '
    + 'autocomplete="one-time-code"><button type="submit">Verify</button></form>';
  w = page('https://github.com/sessions/two-factor/app', totp);
  r = w.eval(six);
  check('two-factor app code field', r === 'submitted' && w.document.querySelector('#app_totp').value === '123456', r);

  const hidden = '<form><input type="hidden" name="otp" value=""><input type="text" id="otp" name="otp" disabled>'
    + '<input type="text" name="email"></form>';
  w = page('https://github.com/sessions/verified-device', hidden);
  r = w.eval(six);
  check('hidden and disabled fields: nothing filled ("none")', r === 'none'
    && w.document.querySelector('input[name=email]').value === '', r);

  w = page('https://github.com/sessions/verified-device', '<p>Loading…</p>');
  r = w.eval(six);
  check('no field yet: "none"', r === 'none', r);

  const eightBox = '<form><input type="text" id="otp" name="otp" autocomplete="one-time-code"><button type="submit">Go</button></form>';
  w = page('https://github.com/sessions/verified-device', eightBox);
  r = w.eval(eight);
  check('an 8-digit code', w.document.querySelector('#otp').value === '12345678', w.document.querySelector('#otp').value);

  if (probe && send) {
    // GitHub's "Confirm access" (sudo) page as the owner's 22 Nov screenshot shows it
    const sudo = '<title>Confirm access</title><h1>Confirm access</h1><p>Signed in as @owner</p>'
      + '<form action="/sessions/sudo" method="post"><button type="submit">Use passkey</button></form>'
      + '<a href="/sessions/sudo?mobile">GitHub Mobile</a><a href="/sessions/sudo?totp">Authentication code</a>'
      + '<p>Having problems?</p><form action="/sessions/sudo/send_email_otp" method="post" id="mailform">'
      + '<button type="submit" class="btn-link">Send a code via email</button></form>'
      + '<input type="text" name="q" placeholder="Search">';
    w = page('https://github.com/login/device/confirm', sudo);
    r = w.eval(probe);
    check('Confirm access: no code field, "Send a code via email" found, confirm access', r === '0|1||1|0|0', r);
    let sent = 0;
    w.document.getElementById('mailform').addEventListener('submit', e => { sent++; e.preventDefault(); });
    r = w.eval(send);
    check('E-mail me a code presses GitHub\'s own "Send a code via email" once', r === 'clicked' && sent === 1, r + ' ' + sent);

    // the e-mailed sudo code page
    const mailed = '<title>Confirm access</title><h1>Confirm access</h1>'
      + '<p>Enter the verification code sent to b***@gmail.com</p>'
      + '<form action="/sessions/sudo" method="post"><input type="text" name="sudo_otp" placeholder="XXXXXXXX" '
      + 'autocomplete="off" maxlength="8"><button type="submit">Verify</button>'
      + '<button type="submit" formaction="/sessions/sudo/resend">Resend code</button></form>';
    w = page('https://github.com/login/device/confirm', mailed);
    r = w.eval(probe);
    check('sudo code page: a code field and the address the code went to', r === '1|0|b***@gmail.com|1|0|8', r);
    r = w.eval(eight);
    await wait(700);
    check('sudo code page: 8-digit code filled into GitHub\'s field, Verify pressed once',
      r === 'submitted' && w.document.querySelector('input[name=sudo_otp]').value === '12345678' && w.submits === 1,
      r + ' ' + w.document.querySelector('input[name=sudo_otp]').value + ' ' + w.submits);

    // a field GitHub only marks by its placeholder (XXXXXX) and numeric input
    const plain = '<form><input type="text" id="x1" inputmode="numeric" maxlength="6" placeholder="XXXXXX">'
      + '<button type="submit">Verify</button></form>';
    w = page('https://github.com/sessions/something-new', plain);
    r = w.eval(probe);
    check('an unknown code page: the field found by its placeholder / numeric input', r === '1|0||0|0|6', r);
    r = w.eval(six);
    check('…and filled', r === 'submitted' && w.document.querySelector('#x1').value === '123456', r);

    // the device-code page (the window fills it itself): never a code page
    const device = '<form action="/login/device" method="post">'
      + [0, 1, 2, 3, 4, 5, 6, 7].map(i => '<input type="text" maxlength="1" name="user-code-' + i + '" id="user-code-' + i + '">').join('')
      + '<button type="submit">Continue</button></form>';
    w = page('https://github.com/login/device', device);
    r = w.eval(probe);
    check('device-code page: no code field, nothing to send', r === '0|0||0|0|0', r);
    r = w.eval(eight);
    check('device-code page: the code helper never types into the device-code boxes', r === 'none'
      && Array.from(w.document.querySelectorAll('input')).every(i => i.value === ''), r);

    // the sign-in page: user name and password are never code fields
    const login = '<form action="/session" method="post"><input type="text" name="login" id="login_field">'
      + '<input type="password" name="password" id="password"><input type="submit" value="Sign in"></form>';
    w = page('https://github.com/login', login);
    r = w.eval(probe);
    check('sign-in page: user name / password are not code fields', r === '0|0||0|0|0', r);

    // review: the address in its own element right after "sent to" (no space in the page's text)
    const tight = '<p>Enter the verification code sent to<strong>m***@outlook.com</strong></p>'
      + '<form><input type="text" name="otp" autocomplete="one-time-code"></form>';
    w = page('https://github.com/sessions/sudo', tight);
    r = w.eval(probe);
    check('the address read from its own element', r === '1|0|m***@outlook.com|0|0|0', r);

    // second review: a hidden "sent to" panel and "consent to" are not where the code went
    const hiddenSent = '<div hidden><p>We sent a code to b***@gmail.com</p></div><p>I consent to admin@example.com</p>'
      + '<form><input type="text" name="app_otp" autocomplete="one-time-code"></form>';
    w = page('https://github.com/sessions/two-factor/app', hiddenSent);
    r = w.eval(probe);
    check('hidden "sent to" text and "consent to" give no address', r === '1|0||0|0|0', r);

    // a hidden "send" link (another choice's form) is not offered
    const hiddenSend = '<div hidden><button>Send a code via email</button></div><p>Use your passkey</p>';
    w = page('https://github.com/login/device/confirm', hiddenSend);
    r = w.eval(probe);
    check('a hidden "Send a code via email" is not offered', r === '0|0||0|0|0', r);
    r = w.eval(send);
    check('…and never pressed', r === 'none', r);

    // v2.1.5 (owner's 23 Nov screenshot): GitHub Mobile's request page: no code field, "Send a code via email" offered
    const mobile = '<title>Two-factor authentication</title><h1>Two-factor authentication</h1>'
      + '<p>We sent you a verification request on your GitHub Mobile app. Enter the digits shown below to verify.</p>'
      + '<div class="h0">77</div><p>Having problems?</p><a href="/sessions/two-factor/app">Use your authenticator app</a>'
      + '<form action="/sessions/two-factor/send_email_otp" method="post"><button type="submit">Send a code via email'
      + '</button></form><script>var t = "GitHub Mobile request sent";</script>';
    w = page('https://github.com/sessions/two-factor/mobile', mobile);
    r = w.eval(probe);
    check('GitHub Mobile page: no code field, "Send a code via email", GitHub Mobile request', r === '0|1||0|1|0', r);

    // the authenticator page that only links to GitHub Mobile as another way: a code page, not GitHub Mobile's
    const totp = '<h1>Two-factor authentication</h1><form><input type="text" name="app_otp" autocomplete="one-time-code">'
      + '</form><p>Unable to verify with your authenticator app?</p><a href="/sessions/two-factor/mobile">Use GitHub Mobile'
      + '</a>';
    w = page('https://github.com/sessions/two-factor/app', totp);
    r = w.eval(probe);
    check('authenticator page with a GitHub Mobile link: a code page', r === '1|0||0|0|0', r);

    // a hidden script or link that names GitHub Mobile on another page is not GitHub Mobile's request
    const other = '<p>Use your passkey</p><a href="/x">GitHub Mobile request sent</a>'
      + '<script>var s = "We sent a request to GitHub Mobile";</script>';
    w = page('https://github.com/sessions/sudo', other);
    r = w.eval(probe);
    check('a link or script naming GitHub Mobile is not its request page', r === '0|0||0|0|0', r);

    // v2.1.6 (owner's screenshot: "Please match the format requested" after a 6-digit code went into the 8-digit
    // e-mailed code field): the field's length is read and a code of another length is never typed
    const eightField = '<p>Enter the verification code sent to b***@gmail.com</p><form action="/sessions/verified-device" '
      + 'method="post"><input type="text" id="otp" name="otp" autocomplete="one-time-code" pattern="[0-9]{8}" '
      + 'placeholder="XXXXXXXX" required><button type="submit">Verify</button></form>';
    w = page('https://github.com/sessions/verified-device', eightField);
    r = w.eval(probe);
    check('8-digit e-mailed code page: the probe says 8 digits', r === '1|0|b***@gmail.com|0|0|8', r);
    r = w.eval(six);
    await wait(700);
    check('8-digit field: a 6-digit code is refused ("length:8"), nothing typed, nothing sent',
      r === 'length:8' && w.document.querySelector('#otp').value === '' && w.inputs === 0 && w.submits === 0,
      r + ' ' + w.document.querySelector('#otp').value + ' ' + w.submits);
    r = w.eval(eight);
    await wait(700);
    check('8-digit field: the 8-digit code goes in, Verify pressed once',
      r === 'submitted' && w.document.querySelector('#otp').value === '12345678' && w.submits === 1, r);

    const patternOnly = '<form><input type="text" name="otp" pattern="\\d{8}"><button type="submit">Verify</button></form>';
    w = page('https://github.com/sessions/verified-device', patternOnly);
    r = w.eval(probe);
    check('a field marked only by its pattern \\d{8}: 8 digits', r === '1|0||0|0|8', r);
    check('…a 6-digit code refused', w.eval(six) === 'length:8' && w.document.querySelector('input[name=otp]').value === '');

    const sixField = '<form><input type="text" name="otp" maxlength="6" pattern="[0-9]{6}"><button>Verify</button></form>';
    w = page('https://github.com/sessions/two-factor/app', sixField);
    r = w.eval(eight);
    check('6-digit field: an 8-digit code refused ("length:6")', r === 'length:6'
      && w.document.querySelector('input[name=otp]').value === '', r);

    const eightBoxes = '<form>' + [0, 1, 2, 3, 4, 5, 6, 7].map(i => '<input type="tel" maxlength="1" name="c' + i + '">').join('')
      + '<button>Verify</button></form>';
    w = page('https://github.com/sessions/verified-device', eightBoxes);
    r = w.eval(probe);
    check('eight one-digit boxes: the probe says 8', r === '1|0||0|0|8', r);
    r = w.eval(six);
    check('eight boxes: a 6-digit code refused, boxes empty', r === 'length:8'
      && Array.from(w.document.querySelectorAll('input')).every(i => i.value === ''), r);

    const either = '<form><input type="text" name="otp" pattern="[0-9]{6}|[0-9]{8}"><button>Verify</button></form>';
    w = page('https://github.com/sessions/verified-device', either);
    r = w.eval(probe);
    check('a field taking 6 or 8 digits: length not said (0)', r === '1|0||0|0|0', r);
    check('…and a 6-digit code goes in', w.eval(six) === 'submitted' && w.document.querySelector('input[name=otp]').value === '123456');
  }

  console.log('OTPSCRIPT ' + ok + '/' + total + (ok === total ? ' ok' : ' FAILED'));
  process.exit(ok === total ? 0 : 1);
})();
