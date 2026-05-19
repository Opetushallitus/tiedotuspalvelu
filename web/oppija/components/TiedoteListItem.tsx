import React from "react";
import ReactMarkdown from "react-markdown";

import type { TiedoteDto } from "../api";
import { formatFinnishDate } from "../date";
import { useLocalisations } from "../useLocalisations";

export function TiedoteListItem({ tiedote }: { tiedote: TiedoteDto }) {
  const dateText = formatFinnishDate(tiedote.createdAt);
  const { t } = useLocalisations();

  return (
    <li className="tp__item">
      <span className="tp__date">{dateText}</span>
      <div data-testid="omatViestitViestiContent" className="tp__text">
        <ReactMarkdown>
          {t("OMAT_VIESTIT_KIELITUTKINTOTODISTUS_VIESTI")}
        </ReactMarkdown>
        <a className="tp__link" target="_blank" href="/koski/omattiedot">
          {t("OMAT_VIESTIT_KIELITUTKINTOTODISTUS_LINKKI")}
        </a>
      </div>
    </li>
  );
}
