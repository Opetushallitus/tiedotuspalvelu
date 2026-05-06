import { createApi, fetchBaseQuery } from "@reduxjs/toolkit/query/react";

export type MeResponse = {
  nimi: string;
};

export type LocalisationDto = {
  key: string;
  locale: string;
  value: string;
};

export type StateCount = {
  state: string;
  description: string;
  count: number;
  retriedCount: number;
  retriedThreeOrMore: number;
};

export type TiedoteSummary = {
  stateCounts: StateCount[];
};

export const api = createApi({
  reducerPath: "api",
  baseQuery: fetchBaseQuery({ baseUrl: "/tiedotuspalvelu/ui" }),
  tagTypes: ["me"],
  endpoints: (builder) => ({
    getMe: builder.query<MeResponse, void>({
      query: () => "me",
      providesTags: ["me"],
    }),
    getLocalisations: builder.query<LocalisationDto[], void>({
      query: () => "localisations",
    }),
    getTiedoteSummary: builder.query<TiedoteSummary, void>({
      query: () => "tiedotteet/summary",
    }),
  }),
});

export const {
  useGetMeQuery,
  useGetLocalisationsQuery,
  useGetTiedoteSummaryQuery,
} = api;
