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
  await expect(tiedoteSection).toContainText("Sinulle ei ole uusia viestejä");

  const linkit = tiedoteSection.getByRole("link", {
    name: "Viestini Oma Opintopolussa",
  });
  await expect(linkit).toHaveCount(2);
  await expect(linkit.first()).toHaveAttribute(
    "href",
    "https://opintopolku.fi/konfo/fi/sivu/oma-opintopolku#viestini",
  );
  await expect(linkit.nth(1)).toHaveAttribute(
    "href",
    "https://www.oph.fi/fi/palvelut/viestini-oma-opintopolussa",
  );
});
