import { expect, test } from "@playwright/test";
import { randomUUID } from "crypto";
import {
  createTiedote,
  reset,
  OPPIJANUMERO_SEVILLANTES_HENNAKARINA,
  generateOpiskeluoikeusOid,
  runSendSuomiFiViestitTask,
  runValidateTiedoteTask,
  downloadReportAndFindLine,
  runFetchKielitutkintotodistusTask,
} from "./test-helpers";

test.beforeAll(async ({ request }) => {
  await reset(request);
});

test("Kielitutkintotodistus - Suomi.fi-viestit ei käytössä", async ({
  page,
  request,
}) => {
  const todistusUuid = randomUUID();
  const tiedoteApiResponse = await test.step("Tiedote luodaan", async () => {
    return await createTiedote(request, {
      oppijanumero: OPPIJANUMERO_SEVILLANTES_HENNAKARINA,
      idempotencyKey: randomUUID(),
      todistusBucket: "bucket",
      todistusKey: `${todistusUuid}/todistus.pdf`,
      opiskeluoikeusOid: await generateOpiskeluoikeusOid(request),
      kituExamineeDetails: {
        etunimet: "Henna Kaarina",
        sukunimi: "Sevillantes",
        katuosoite: "Testikatu e2e A 11 F",
        postinumero: "00100",
        postitoimipaikka: "HELSINKI",
        maa: {
          koodiarvo: "FIN",
          koodistoUri: "maatjavaltiot1",
        },
        todistuskieli: {
          koodiarvo: "FI",
          koodistoUri: "kieli",
        },
      },
    });
  });

  await test.step("Virkailija näkee tiedoteen rapsalla", async () => {
    await page.goto("/tiedotuspalvelu/");
    await page.getByRole("button", { name: "Riina Raportoija" }).click();
    await expect(
      page.getByRole("button", { name: "Lataa kaikki tiedotteet CSV:nä" }),
    ).toBeVisible();

    expect(
      await downloadReportAndFindLine(page, tiedoteApiResponse.id),
    ).toMatchObject({
      "Tiedotteen vastaanottajan oppijanumero":
        OPPIJANUMERO_SEVILLANTES_HENNAKARINA,
      "Tiedotteen käsittelyn tila tiedotuspalvelussa":
        "TIEDOTTEEN_JA_OPPIJAN_VALIDOINTI",
      "Kielitutkintotodistuksen S3 URL": `s3://bucket/${todistusUuid}/todistus.pdf`,
    });
    await runValidateTiedoteTask(request);
    expect(
      await downloadReportAndFindLine(page, tiedoteApiResponse.id),
    ).toMatchObject({
      "Tiedotteen käsittelyn tila tiedotuspalvelussa":
        "SUOMIFI_VIESTIN_LÄHETYS",
    });
    await runSendSuomiFiViestitTask(request);
    expect(
      await downloadReportAndFindLine(page, tiedoteApiResponse.id),
    ).toMatchObject({
      "Tiedotteen käsittelyn tila tiedotuspalvelussa":
        "KIELITUTKINTOTODISTUKSEN_NOUTO",
    });
    await runFetchKielitutkintotodistusTask(request);
    expect(
      await downloadReportAndFindLine(page, tiedoteApiResponse.id),
    ).toMatchObject({
      "Tiedotteen käsittelyn tila tiedotuspalvelussa":
        "SUOMIFI_VIESTIN_LÄHETYS_PAPERIPOSTIOPTIOLLA",
    });
    await runSendSuomiFiViestitTask(request);
    expect(
      await downloadReportAndFindLine(page, tiedoteApiResponse.id),
    ).toMatchObject({
      "Tiedotteen käsittelyn tila tiedotuspalvelussa": "TIEDOTE_KÄSITELTY",
    });
  });

  await test.step("Oppija näkee tiedotteen omat-viestit sivulla", async () => {
    await page.goto("/omat-viestit/");
    await page
      .getByRole("button", { name: "Hennakaarina Sevillantes (181064-998C)" })
      .click();
    await expect(
      page.getByText(
        "Opetushallitus on myöntänyt sinulle todistuksen yleisestä kielitutkinnosta",
        { exact: false },
      ),
    ).toBeVisible();

    // formats content based on markdown, the first element should be h1
    const titleHtml = await page
      .getByTestId("omatViestitViestiContent")
      .innerHTML();
    expect(titleHtml.substring(0, 4)).toEqual("<h1>");

    await test.step("todistuslinkki vie Kosken omat tiedot näkymään", async () => {
      const linkki = page.getByRole("link", {
        name: "Siirry Opintoni-sivulle tästä linkistä",
      });
      const hreffi = await linkki.getAttribute("href");
      await expect(hreffi).toBe("/koski/omattiedot");
    });
  });
});
