import { expect, test } from "@playwright/test";
import { proxyOppijaRaamit, reset } from "./test-helpers";

test.beforeAll(async ({ request }) => {
  await reset(request);
});

test("Omat-viestit - eIDAS-tunnistautuneen nimi näytetään raameissa etu- ja sukunimestä", async ({
  page,
}) => {
  await proxyOppijaRaamit(page);

  await page.goto("/omat-viestit/");
  await page
    .getByRole("button", { name: "Leon Elias Germany (eIDAS)" })
    .click();

  const header = page.getByRole("banner");
  await expect(
    header.getByRole("button", { name: "Leon Elias Germany", exact: true }),
  ).toBeVisible();
});
