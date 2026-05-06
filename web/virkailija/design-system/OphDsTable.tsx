import React, { ReactNode } from "react";

import "./oph-ds-table.css";

export type TableProps = {
  children: ReactNode;
};

export function Table({ children }: TableProps) {
  return (
    <table className="oph-ds-table oph-ds-table--compact">{children}</table>
  );
}

export type HeaderProps = {
  children: ReactNode;
};
export function Header({ children }: HeaderProps) {
  return <thead>{children}</thead>;
}

export type BodyProps = {
  children: ReactNode;
};
export function Body({ children }: BodyProps) {
  return <tbody>{children}</tbody>;
}

export type RowProps = {
  children: ReactNode;
};
export function Row({ children }: RowProps) {
  return <tr>{children}</tr>;
}
export type CellProps = {
  children: ReactNode;
};
export function Cell({ children }: CellProps) {
  return <td>{children}</td>;
}
