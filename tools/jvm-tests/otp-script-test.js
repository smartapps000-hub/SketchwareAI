// v2.1.2: runs OtpAssist's fill script (printed by OtpScriptPrint) in jsdom against stand-ins of GitHub's verification
// pages: the e-mailed device code (one field + Verify), a page that sends the form by itself on input, six one-digit
// boxes, a two-factor app code field, a hidden / disabled field, no field. GitHub's real pages can't be opened from the
// build machine; this checks the script's logic and syntax only.
// Usage: node otp-script-test.js <script for 123456> <script for 12345678>   (needs jsdom)
const fs = require('fs');
const { JSDOM } = require('jsdom');
const six = fs.readFileSync(process.argv[2], 'utf8');
const eight = fs.readFileSync(process.argv[3], 'utf8');
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

  console.log('OTPSCRIPT ' + ok + '/' + total + (ok === total ? ' ok' : ' FAILED'));
  process.exit(ok === total ? 0 : 1);
})();
