import React from "react";
import { useGetMeQuery, useGetTiedoteSummaryQuery } from "./api";
import { OphDsPage } from "./design-system/OphDsPage";
import { OphDsButton } from "./design-system/OphDsButton";
import { Table, Header, Row, Body, Cell } from "./design-system/OphDsTable";
import { useLocalisations } from "./useLocalisations";
export function App() {
  const meQuery = useGetMeQuery();
  return meQuery.isSuccess ? <RealApp /> : <LoginScreen />;
}

function LoginScreen() {
  const reutrnUrl = encodeURIComponent(
    window.location.origin + "/tiedotuspalvelu/j_spring_cas_security_check",
  );
  const casLoginUrl = `http://localhost:8888/realms/cas-virkailija/protocol/cas/login?service=${reutrnUrl}`;
  return (
    <OphDsPage header="Tiedotuspalvelu">
      <OphDsButton
        variant="primary"
        onClick={() => (window.location.href = casLoginUrl)}
      >
        Kirjaudu sisään
      </OphDsButton>
    </OphDsPage>
  );
}
function RealApp() {
  const { t } = useLocalisations();
  return (
    <OphDsPage header={t("TIEDOTUSPALVELU_OTSIKKO")}>
      <CsvButton />
      <h1>{t("TIEDOTUSPALVELU_TILASTOJA_OTSIKKO")}</h1>
      <TiedoteSummary />
    </OphDsPage>
  );
}

function TiedoteSummary() {
  const { t } = useLocalisations();
  const { data, isLoading, isError } = useGetTiedoteSummaryQuery();

  if (isLoading) return <p>{t("TIEDOTUSPALVELU_LADATAAN")}</p>;
  if (isError || !data) return <p>{t("TIEDOTUSPALVELU_VIRHE")}</p>;

  return (
    <Table>
      <Header>
        <Row>
          <Cell>{t("TIEDOTTEEN_TILA")}</Cell>
          <Cell>{t("TILAN_KUVAUS")}</Cell>
          <Cell>{t("TIEDOTE_LKM")}</Cell>
          <Cell>{t("TIEDOTE_LKM_RETRY")}</Cell>
          <Cell>{t("TIEDOTE_LKM_JUMISSA")}</Cell>
        </Row>
      </Header>
      <Body>
        {data.stateCounts.map((sc) => (
          <Row key={sc.state}>
            <Cell>
              <code>{sc.state}</code>
            </Cell>
            <Cell>{sc.description}</Cell>
            <Cell>{formatNumber(sc.count)}</Cell>
            <Cell>{formatNumber(sc.retriedCount)}</Cell>
            <Cell>{formatNumber(sc.retriedThreeOrMore)}</Cell>
          </Row>
        ))}
      </Body>
    </Table>
  );
}

function formatNumber(n: number): string {
  return n.toLocaleString("fi-FI");
}

function CsvButton() {
  const { t } = useLocalisations();
  return (
    <OphDsButton
      variant="primary"
      onClick={() =>
        (window.location.pathname = "/tiedotuspalvelu/ui/tiedotteet/csv")
      }
    >
      {t("TIEDOTUSPALVELU_LATAA_CSV")}
    </OphDsButton>
  );
}
