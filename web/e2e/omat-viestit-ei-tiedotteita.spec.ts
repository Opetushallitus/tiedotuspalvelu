import { expect, test } from "@playwright/test";
import { reset } from "./test-helpers";

test.beforeAll(async ({ request }) => {
  await reset(request);
});

test("Omat-viestit - tyhjän tilan teksti renderöidään markdownina", async ({
  page,
}) => {
  await page.goto("/omat-viestit/");
  await page.getByRole("button", { name: "Nordea Demo (210281-9988)" }).click();

  const tiedoteSection = page.getByLabel("Viestit");
  await expect(tiedoteSection).toContainText("Sinulle ei ole viestejä");

  // Markdown-linkki renderöityy <a>-elementiksi, ei raakana [Lue lisää](...) -tekstinä
  const lueLisaa = tiedoteSection.getByRole("link", { name: "Lue lisää" });
  await expect(lueLisaa).toBeVisible();
  await expect(lueLisaa).toHaveAttribute("href", "https://opintopolku.fi");
});
