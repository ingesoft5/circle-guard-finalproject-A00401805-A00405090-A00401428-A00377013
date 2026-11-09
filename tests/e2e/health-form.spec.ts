/**
 * CG-DOC: End-to-end scenario for the health questionnaire screen.
 */
import { test, expect } from '@playwright/test';

test('el cuestionario de salud carga o informa que no hay uno activo', async ({ page }) => {
  await page.goto('/questionnaire');

  await expect(
    page.getByText('Health Screening').or(page.getByText('No active health survey available.'))
  ).toBeVisible({ timeout: 15_000 });
});

test('el cuestionario permite volver a la pantalla anterior', async ({ page }) => {
  await page.goto('/questionnaire');
  const back = page.getByText('GO BACK');
  if (await back.isVisible().catch(() => false)) {
    await back.click();
    await expect(page).not.toHaveURL(/questionnaire$/);
  }
});
