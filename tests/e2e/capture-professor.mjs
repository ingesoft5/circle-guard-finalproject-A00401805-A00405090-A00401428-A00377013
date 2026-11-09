// CG-DOC: Captures professor read-only evidence from Jenkins, SonarQube, and Nexus.
import { chromium } from '@playwright/test';
import { mkdir } from 'node:fs/promises';

const out = process.env.EVIDENCE_DIR || '../../docs/informe/evidencia';
const tools = process.env.TOOLS_IP;
const user = 'professor';
const sonarPass = process.env.PROFESSOR_SONAR_PASSWORD;
const nexusPass = process.env.PROFESSOR_NEXUS_PASSWORD;
await mkdir(out, { recursive: true });

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, ignoreHTTPSErrors: true });
const page = await context.newPage();

async function shot(name) {
  await page.screenshot({ path: `${out}/${name}.png`, fullPage: true });
  console.log(`saved ${name}.png`);
}

try {
  await page.goto(`http://${tools}:8080/login`, { waitUntil: 'domcontentloaded' });
  await page.fill('input[name="j_username"]', user);
  await page.fill('input[name="j_password"]', process.env.PROFESSOR_PASSWORD);
  await page.click('button[type="submit"]');
  await page.waitForTimeout(4000);
  await page.goto(`http://${tools}:8080/job/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(2000);
  await shot('jenkins-professor');
} catch (e) {
  console.error('jenkins evidence:', e.message);
  await shot('jenkins-professor');
}

try {
  await page.goto(`http://${tools}:9000/sessions/new`, { waitUntil: 'domcontentloaded' });
  await page.fill('#login', user);
  await page.fill('#password', sonarPass);
  await page.click('button[type="submit"]');
  await page.waitForTimeout(8000);
  await page.goto(`http://${tools}:9000/dashboard?id=circleguard-gateway-service`, { waitUntil: 'networkidle', timeout: 60000 }).catch(() => {});
  await page.waitForTimeout(5000);
  await shot('sonarqube-professor');
} catch (e) {
  console.error('sonar evidence:', e.message);
  await shot('sonarqube-professor');
}

try {
  await page.goto(`http://${tools}:8081/`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(3000);
  const signIn = page.getByText('Sign in', { exact: true }).first();
  if (await signIn.count()) await signIn.click();
  await page.waitForTimeout(1500);
  if (await page.locator('input[name="username"]').count()) {
    await page.fill('input[name="username"]', user);
    await page.fill('input[name="password"]', nexusPass);
    await page.getByRole('button', { name: /sign in/i }).last().click();
    await page.waitForTimeout(4000);
  }
  await page.goto(`http://${tools}:8081/#browse/browse:circleguard-docker`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(3000);
  await shot('nexus-professor');
} catch (e) {
  console.error('nexus evidence:', e.message);
  await shot('nexus-professor');
}

await browser.close();
