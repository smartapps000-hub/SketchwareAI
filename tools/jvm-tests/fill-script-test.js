// v2.0.0: runs the code fill script of GitHubInAppAuthDialog (printed by FillScriptPrint) in jsdom against stand-ins
// of GitHub's device page: eight one-letter boxes (with a search box in the header), one code box, the wrong page, no
// boxes. GitHub's real page can't be opened from the build machine; this checks the script's logic and syntax only.
// Usage: node fill-script-test.js <script for ABCD-1234> <script for an injection attempt>   (needs jsdom)
const fs = require('fs');
const { JSDOM } = require('jsdom');
const script = fs.readFileSync(process.argv[2], 'utf8');
const injected = fs.readFileSync(process.argv[3], 'utf8');
let ok = 0, total = 0;
function check(name, cond, detail) {
  total++; if (cond) ok++;
  console.log('FILL CHECK ' + (cond ? 'ok  ' : 'FAIL') + ' ' + name + (detail === undefined ? '' : ' - ' + detail));
}
function page(url, body) {
  const dom = new JSDOM('<!doctype html><html><body>' + body + '</body></html>', { url, runScripts: 'outside-only' });
  const w = dom.window;
  // jsdom has no layout: every element counts as visible unless marked hidden
  w.HTMLElement.prototype.getBoundingClientRect = function () {
    return this.hasAttribute('data-hidden') ? { width: 0, height: 0 } : { width: 20, height: 20 };
  };
  w.events = 0;
  w.submitted = false;
  w.document.addEventListener('input', () => w.events++, true);
  w.document.addEventListener('submit', e => { w.submitted = true; e.preventDefault(); }, true);
  w.document.addEventListener('click', () => { w.submitted = true; }, true);
  return w;
}
const eight = '<header><input type="text" name="q" placeholder="Search"></header>'
  + '<form action="/login/device" method="post"><input type="hidden" name="authenticity_token" value="x">'
  + [0,1,2,3].map(i => '<input type="text" maxlength="1" name="user-code-' + i + '">').join('') + '<span>-</span>'
  + [4,5,6,7].map(i => '<input type="text" maxlength="1" name="user-code-' + i + '">').join('')
  + '<input type="submit" value="Continue"></form>';
let w = page('https://github.com/login/device', eight);
let r = w.eval(script);
const boxes = Array.from(w.document.querySelectorAll('input[maxlength="1"]')).map(i => i.value).join('');
check('eight one-letter boxes: filled in order, hyphen skipped', r === 'filled8' && boxes === 'ABCD1234', r + ' ' + boxes);
check('header search box left alone', w.document.querySelector('input[name=q]').value === '', w.document.querySelector('input[name=q]').value);
check('input events fired (8)', w.events === 8, w.events);
check('Continue not pressed (no submit, no click)', w.submitted === false);

w = page('https://github.com/login/device', '<form action="/login/device"><input type="text" name="user_code" id="user_code"><button>Continue</button></form>');
r = w.eval(script);
check('one code box: the whole code with hyphen', r === 'filled1' && w.document.querySelector('#user_code').value === 'ABCD-1234', r + ' ' + w.document.querySelector('#user_code').value);

w = page('https://github.com/login/device', '<form><input type="text" maxlength="9" name="code"></form>');
r = w.eval(script);
check('one box of 9: the code with its hyphen', r === 'filled1' && w.document.querySelector('input').value === 'ABCD-1234', w.document.querySelector('input').value);

w = page('https://github.com/login', '<form><input type="text" name="login"><input type="password" name="password"></form>');
r = w.eval(script);
check('sign-in page: nothing touched', r === 'skip' && w.document.querySelector('input[name=login]').value === '', r);

w = page('https://github.com/login/device', '<p>Loading</p>');
r = w.eval(script);
check('boxes not there yet: none (the app tries again)', r === 'none:0', r);

w = page('https://github.com/login/device', '<form><input type="text" name="login" value=""><input type="text" name="other"></form>');
r = w.eval(script);
check('two unrelated boxes: nothing filled', r === 'none:2' && w.document.querySelector('input[name=login]').value === '', r);

w = page('https://github.com/login/device', eight.replace('name="user-code-7"', 'name="user-code-7" data-hidden="1"'));
r = w.eval(script);
check('a hidden box isn\'t counted (7 visible: nothing filled, the app tries again)', r === 'partial:7'
  && Array.from(w.document.querySelectorAll('input[maxlength="1"]')).every(i => i.value === ''), r);

const arg = injected.slice(injected.lastIndexOf("('") + 2, injected.lastIndexOf("')"));
check('injection attempt: the code argument keeps only letters, digits and hyphens', /^[A-Za-z0-9-]+$/.test(arg)
  && injected.endsWith("('" + arg + "')"), arg);
w = page('https://github.com/login/device', eight);
let ran = true;
try { w.eval(injected); } catch (e) { ran = false; }
check('injection attempt runs as plain text (no syntax break)', ran);
console.log('FILLSCRIPT ' + ok + '/' + total + (ok === total ? ' ok' : ' FAILED'));
process.exit(ok === total ? 0 : 1);
