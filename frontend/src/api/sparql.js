import { API } from "./endpoints";
import http from "./http.js";

export async function searchArticles(req) {
    const { data } = await http.post(API.SEARCH, req);
    return data; // SPARQL Results JSON
}

export async function runSparql(query) {
    const { data } = await http.post(API.SPARQL, { query, format: "json" });
    return data; // SPARQL Results JSON
}
