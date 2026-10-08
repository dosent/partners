import { test, expect } from '@playwright/test';

async function assignmentQueue(page) {
  let item = { id: 'request-id', number: 'SR-001', title: 'Java-разработчик', headcount: 1,
    status: 'NEW', createdAt: '2026-10-06T08:00:00Z', assignedResourceManagerUsername: 'rm.one', version: 1 };
  let conflict = false;
  const commands = [];
  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const json = (body, status = 200) => route.fulfill({ status, json: body });
    if (path === '/api/v1/auth/staff/session') return json({ username: 'staff', accessState: 'ACTIVE',
      permissions: ['staffing.request.manage'] });
    if (path === '/api/v1/auth/csrf') return json({ token: 'csrf', headerName: 'X-XSRF-TOKEN' });
    if (path.endsWith('/assignees')) return json(['rm.one', 'rm.two', 'rm.three'].map(username => ({
      username, role: 'RESOURCE_MANAGER',
    })));
    if (path === '/api/v1/admin/staffing-requests') return json({ items: [item], page: 0, size: 20, total: 1 });
    if (request.method() === 'PUT') {
      const command = request.postDataJSON();
      commands.push(command);
      if (conflict) return json({ message: 'Заявка изменена.', code: 'CONFLICT' }, 409);
      expect(command.expectedVersion).toBe(item.version);
      item = { ...item, assignedResourceManagerUsername: command.username, version: item.version + 1 };
      return json(item);
    }
    return json({});
  });
  await page.goto('/staff/staffing-requests');
  const select = page.getByRole('combobox', { name: 'Ответственный для заявки SR-001' });
  await expect(select).toHaveValue('rm.one');
  return { select, save: page.getByRole('button', { name: 'Сохранить назначение' }), commands,
    changeOnServer(username) { item = { ...item, assignedResourceManagerUsername: username, version: item.version + 1 }; },
    setConflict(value) { conflict = value; },
    async refresh(username) {
      await page.getByRole('button', { name: 'Обновить', exact: true }).click();
      await expect(page.locator('.staff-staffing-summary strong')).toHaveText(username);
      await expect(select).toBeVisible();
    },
  };
}

test('refresh replaces an untouched selector with the current server assignment', async ({ page }) => {
  const queue = await assignmentQueue(page);
  queue.changeOnServer('rm.two');
  await queue.refresh('rm.two');
  await expect(queue.select).toHaveValue('rm.two');
  await expect(queue.save).toBeDisabled();
  expect(queue.commands).toHaveLength(0);
});

test('409 and refresh preserve an edited draft, and successful save clears it', async ({ page }) => {
  const queue = await assignmentQueue(page);
  await queue.select.selectOption('rm.three');
  queue.changeOnServer('rm.two');
  queue.setConflict(true);
  await queue.save.click();
  await expect(page.getByRole('alert')).toContainText('Ваш выбор сохранён');
  await expect(queue.select).toHaveValue('rm.three');
  await queue.refresh('rm.two');
  await expect(queue.select).toHaveValue('rm.three');
  await expect(queue.save).toBeEnabled();
  queue.setConflict(false);
  await queue.save.click();
  await expect(page.getByRole('status')).toContainText('сохранён');
  expect(queue.commands).toEqual([
    { username: 'rm.three', expectedVersion: 1 },
    { username: 'rm.three', expectedVersion: 2 },
  ]);
  queue.changeOnServer('rm.one');
  await queue.refresh('rm.one');
  await expect(queue.select).toHaveValue('rm.one');
  await expect(queue.save).toBeDisabled();
});

test('returning the selector to its original value clears the edited draft', async ({ page }) => {
  const queue = await assignmentQueue(page);
  await queue.select.selectOption('rm.three');
  await queue.select.selectOption('rm.one');
  queue.changeOnServer('rm.two');
  await queue.refresh('rm.two');
  await expect(queue.select).toHaveValue('rm.two');
  await expect(queue.save).toBeDisabled();
});
