/**
 * CG-DOC: End-to-end scenario for the secure login screen.
 */
import { test, expect } from '@playwright/test';

test('el formulario de inicio de sesión está disponible', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByText('Welcome Back')).toBeVisible();
  await expect(page.getByPlaceholder('Username')).toBeVisible();
  await expect(page.getByPlaceholder('Password')).toBeVisible();
  await expect(page.getByText('SIGN IN', { exact: true })).toBeVisible();
});

test('el inicio de sesión exige usuario y contraseña', async ({ page }) => {
  await page.goto('/login');
  await page.getByText('SIGN IN', { exact: true }).click();
  await expect(page.getByText('Welcome Back')).toBeVisible();
  await expect(page.getByPlaceholder('Username')).toBeVisible();
  await expect(page).toHaveURL(/\/login/);
});
