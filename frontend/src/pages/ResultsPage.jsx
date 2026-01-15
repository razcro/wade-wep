import { useMemo } from "react";
import { Link, useLocation } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { searchArticles } from "../api/sparql";
import { v, bindings } from "../models/sparql";

function useParams() {
    const { search } = useLocation();
    return useMemo(() => new URLSearchParams(search), [search]);
}

export default function ResultsPage() {
    const p = useParams();

    const req = {
        query: p.get("q") ?? "",
        language: p.get("lang") ?? "",
        mediaType: p.get("type") ?? "",
        maxWords: p.get("maxWords") ? Number(p.get("maxWords")) : undefined,
        dateFrom: p.get("from") ?? "",
        dateTo: p.get("to") ?? "",
    };

    const { data, isLoading, isError } = useQuery({
        queryKey: ["search", req],
        queryFn: () => searchArticles(req),
    });

    const rows = bindings(data);

    return (
        <div style={{ maxWidth: 1100, margin: "0 auto", padding: 24 }}>
            <h2>Results</h2>
            <p><Link to="/">Back to search</Link></p>

            {isLoading && <p>Loading…</p>}
            {isError && <p>Search failed. Check backend endpoints and proxy.</p>}

            <div style={{ display: "grid", gap: 12 }}>
                {!isLoading && !isError && rows.length === 0 && (
                    <p>No data found.</p>
                )}
                {rows.map((b, i) => {
                    const articleUri = v(b, "article") ?? "";
                    const title = v(b, "title") ?? "(no title)";
                    const description = v(b, "description");
                    const language = v(b, "language");
                    const wordCount = v(b, "wordCount");
                    const published = v(b, "published");
                    const genre = v(b, "genre");

                    console.log('articleUri=  ', articleUri);

                    return (
                        <div key={i} style={{ border: "1px solid #ddd", borderRadius: 8, padding: 12 }}>
                            <h3 style={{ margin: 0 }}>{title}</h3>
                            {description && <p style={{ marginTop: 8 }}>{description}</p>}

                            <div style={{ display: "flex", gap: 12, flexWrap: "wrap", fontSize: 12, color: "#ffffff" }}>
                                {published && <span>Published: {published}</span>}
                                {language && <span>Lang: {language}</span>}
                                {wordCount && <span>Words: {wordCount}</span>}
                                {genre && <span>Type: {genre}</span>}
                            </div>

                            {articleUri && (
                                <p style={{ marginBottom: 0, marginTop: 10 }}>
                                    <Link to={`/article?uri=${encodeURIComponent(articleUri)}`}>Open</Link>
                                </p>
                            )}
                        </div>
                    );
                })}
            </div>
        </div>
    );
}
