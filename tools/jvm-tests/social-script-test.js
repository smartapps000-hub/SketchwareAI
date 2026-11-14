// v2.0.2: the sign-in window's "Continue with Google / Apple" interception (GitHubInAppAuthDialog.SOCIAL_SCRIPT) against
// stand-ins of GitHub's sign-in and sign-up pages in jsdom: a tap on a social button (form, link or text) is cancelled and
// goes to the hand-off address; GitHub's own Sign in / Create an account / Forgot password stay untouched; a second run
// adds nothing. Usage: node social-script-test.js <script file>   (needs jsdom; SocialScriptPrint prints the script)
const fs = require('fs');
const { JSDOM, VirtualConsole } = require('jsdom');
const script = fs.readFileSync(process.argv[2], 'utf8');
let ok = 0, total = 0;
function check(name, pass, detail) {
  total++; if (pass) ok++;
  console.log('SOCIAL CHECK ' + (pass ? 'ok  ' : 'FAIL') + ' ' + name + (detail === undefined ? '' : ' - ' + detail));
}
function page(html) {
  const navigations = [];
  const vc = new VirtualConsole();
  vc.on('jsdomError', e => { if (/navigation/i.test(String(e.message))) navigations.push(String(e.message)); });
  const dom = new JSDOM(html, { url: 'https://github.com/login', runScripts: 'outside-only', virtualConsole: vc });
  return { dom, navigations, run: () => dom.window.eval(script) };
}
function click(dom, el) {
  const ev = new dom.window.MouseEvent('click', { bubbles: true, cancelable: true });
  el.dispatchEvent(ev);
  return ev.defaultPrevented;
}
const login = `<html><body>
  <form action="/session" method="post"><input name="login"><input type="password" name="password">
    <input type="submit" id="signin" value="Sign in"></form>
  <a id="forgot" href="/password_reset">Forgot password?</a>
  <a id="create" href="/signup?source=login">Create an account</a>
  <form action="/sessions/social/google/initiate" method="post"><button type="submit" id="google">
    <svg></svg><span>Continue with Google</span></button></form>
  <a id="apple" href="/sessions/social/apple/initiate?return_to=%2Flogin%2Fdevice">Continue with Apple</a>
  <button id="textonly" type="button"> Sign up with   Google </button>
  <button id="plain" type="button">Continue</button>
</body></html>`;
let p = page(login);
check('the script runs', p.run() === 'ok');
let d = p.dom.window.document;
let before = p.navigations.length;
check('"Continue with Google" (a social form\'s button, the tap on its inner text) is cancelled',
  click(p.dom, d.querySelector('#google span')) === true);
check('... and goes to the hand-off address (one navigation)', p.navigations.length === before + 1, p.navigations.length - before);
before = p.navigations.length;
check('"Continue with Apple" (a /sessions/social link) is cancelled', click(p.dom, d.querySelector('#apple')) === true);
check('... one navigation', p.navigations.length === before + 1);
check('a button known only by its text ("Sign up with Google") is cancelled', click(p.dom, d.querySelector('#textonly')) === true);
before = p.navigations.length;
check('GitHub\'s own Sign in is not touched', click(p.dom, d.querySelector('#signin')) === false);
check('"Forgot password?" is not touched', click(p.dom, d.querySelector('#forgot')) === false);
check('"Create an account" is not touched', click(p.dom, d.querySelector('#create')) === false);
check('a plain "Continue" is not touched', click(p.dom, d.querySelector('#plain')) === false);
const sub = new p.dom.window.Event('submit', { bubbles: true, cancelable: true });
d.querySelector('form[action="/sessions/social/google/initiate"]').dispatchEvent(sub);
check('a social form submitted with Enter is cancelled too', sub.defaultPrevented === true);
check('a second run adds nothing ("again")', p.run() === 'again');
const own = new p.dom.window.Event('submit', { bubbles: true, cancelable: true });
d.querySelector('form[action="/session"]').dispatchEvent(own);
check('the sign-in form\'s own submit is not cancelled', own.defaultPrevented === false);
console.log('SOCIALSCRIPT ' + ok + '/' + total + (ok === total ? ' ok' : ' FAILED'));
process.exit(ok === total ? 0 : 1);
