import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { QRCodeCanvas } from "qrcode.react";
import cytoscape from "cytoscape";
import { runSparql } from "../api/sparql";
import { v, bindings } from "../models/sparql";

function useArticleUri() {
    const { search } = useLocation();
    const p = useMemo(() => new URLSearchParams(search), [search]);
    return p.get("uri") ?? "";
}

function shorten(uri) {
    try {
        const u = new URL(uri);
        const parts = u.pathname.split("/").filter(Boolean);
        return parts.slice(-2).join("/") || uri;
    } catch {
        return uri.length > 48 ? uri.slice(0, 48) + "…" : uri;
    }
}

export default function ArticlePage() {
    const articleUri = useArticleUri();
    const [tab, setTab] = useState("overview"); // overview | provenance | recommend | graph
    const cyContainerRef = useRef(null);
    const cyInstanceRef = useRef(null);

    // OVERVIEW
    const qOverview = useMemo(() => `
    PREFIX schema: <http://schema.org/>
    PREFIX dc: <http://purl.org/dc/elements/1.1/>

    SELECT ?title ?description ?body ?language ?wordCount ?published ?genre ?url ?author ?authorName
    WHERE {
      GRAPH ?g {
        <${articleUri}> a schema:NewsArticle ;
            schema:headline ?title .
        OPTIONAL { <${articleUri}> schema:description ?description . }
        OPTIONAL { <${articleUri}> schema:articleBody ?body . }
        OPTIONAL { <${articleUri}> schema:inLanguage ?language . }
        OPTIONAL { <${articleUri}> schema:wordCount ?wordCount . }
        OPTIONAL { <${articleUri}> schema:datePublished ?published . }
        OPTIONAL { <${articleUri}> schema:genre ?genre . }
        OPTIONAL { <${articleUri}> schema:url ?url . }
        OPTIONAL {
          <${articleUri}> schema:author ?author .
          OPTIONAL { ?author schema:name ?authorName . }
        }
      }
    }
    LIMIT 1
  `, [articleUri]);

    const overview = useQuery({
        queryKey: ["overview", articleUri],
        queryFn: () => runSparql(qOverview),
        enabled: !!articleUri,
    });

    const first = bindings(overview.data)[0] || null;
    const title = first ? v(first, "title") : null;
    const description = first ? v(first, "description") : null;
    const body = first ? v(first, "body") : null;
    const language = first ? v(first, "language") : null;
    const wordCount = first ? v(first, "wordCount") : null;
    const published = first ? v(first, "published") : null;
    const genre = first ? v(first, "genre") : null;
    const url = first ? v(first, "url") : null;
    const authorUri = first ? v(first, "author") : null;
    const authorName = first ? v(first, "authorName") : null;

    // TOPICS (SKOS + dc:subject)
    const qTopics = useMemo(() => `
    PREFIX schema: <http://schema.org/>
    PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
    PREFIX dc: <http://purl.org/dc/elements/1.1/>

    SELECT ?concept ?label ?subject
    WHERE {
      GRAPH ?g {
        OPTIONAL { <${articleUri}> dc:subject ?subject . }
        OPTIONAL {
          <${articleUri}> schema:about ?concept .
          OPTIONAL { ?concept skos:prefLabel ?label . }
        }
      }
    }
    LIMIT 200
  `, [articleUri]);

    const topics = useQuery({
        queryKey: ["topics", articleUri],
        queryFn: () => runSparql(qTopics),
        enabled: !!articleUri && tab === "overview",
    });

    // MEDIA
    const qMedia = useMemo(() => `
    PREFIX schema: <http://schema.org/>
    SELECT ?media ?contentUrl ?format
    WHERE {
      GRAPH ?g {
        <${articleUri}> schema:associatedMedia ?media .
        OPTIONAL { ?media schema:contentUrl ?contentUrl . }
        OPTIONAL { ?media schema:encodingFormat ?format . }
      }
    }
    LIMIT 200
  `, [articleUri]);

    const media = useQuery({
        queryKey: ["media", articleUri],
        queryFn: () => runSparql(qMedia),
        enabled: !!articleUri && tab === "overview",
    });

    // PROVENANCE
    const qProv = useMemo(() => `
    PREFIX prov: <http://www.w3.org/ns/prov#>
    PREFIX schema: <http://schema.org/>

    SELECT ?activity ?used ?when ?agentName
    WHERE {
      GRAPH ?g {
        <${articleUri}> prov:wasGeneratedBy ?activity .
        OPTIONAL { ?activity prov:used ?used . }
        OPTIONAL { ?activity prov:endedAtTime ?when . }
        OPTIONAL {
          ?activity prov:wasAssociatedWith ?agent .
          OPTIONAL { ?agent schema:name ?agentName . }
        }
      }
    }
    LIMIT 200
  `, [articleUri]);

    const prov = useQuery({
        queryKey: ["prov", articleUri],
        queryFn: () => runSparql(qProv),
        enabled: !!articleUri && tab === "provenance",
    });

    // RECOMMENDATIONS
    const qRec = useMemo(() => `
    PREFIX schema: <http://schema.org/>

    SELECT ?other ?title (COUNT(?c) AS ?score)
    WHERE {
      GRAPH ?g {
        <${articleUri}> schema:about ?c .
        ?other a schema:NewsArticle ;
               schema:about ?c ;
               schema:headline ?title .
      }
      FILTER(?other != <${articleUri}>)
    }
    GROUP BY ?other ?title
    ORDER BY DESC(?score)
    LIMIT 10
  `, [articleUri]);

    const rec = useQuery({
        queryKey: ["rec", articleUri],
        queryFn: () => runSparql(qRec),
        enabled: !!articleUri && tab === "recommend",
    });

    // GRAPH (SELECT triples)
    const qGraph = useMemo(() => `
    SELECT ?s ?p ?o
    WHERE {
      GRAPH ?g {
        { BIND(<${articleUri}> AS ?s) <${articleUri}> ?p ?o . }
        UNION
        { <${articleUri}> ?p1 ?mid . ?mid ?p ?o . BIND(?mid AS ?s) }
      }
    }
    LIMIT 400
  `, [articleUri]);

    const graph = useQuery({
        queryKey: ["graph", articleUri],
        queryFn: () => runSparql(qGraph),
        enabled: !!articleUri && tab === "graph",
    });

    // render cytoscape when graph data loads
    useEffect(() => {
        if (tab !== "graph") return;
        if (!cyContainerRef.current) return;

        const rows = bindings(graph.data);
        if (!rows.length) return;

        // destroy previous
        if (cyInstanceRef.current) {
            cyInstanceRef.current.destroy();
            cyInstanceRef.current = null;
        }

        const nodesMap = new Map();
        const edges = [];

        function ensureNode(id) {
            if (!nodesMap.has(id)) nodesMap.set(id, { data: { id, label: shorten(id) } });
        }

        rows.forEach((b, i) => {
            const s = v(b, "s");
            const p = v(b, "p");
            const o = v(b, "o");
            if (!s || !p || !o) return;

            const oIsUri = o.startsWith("http");
            if (!oIsUri) return; // MVP: ignorăm literali

            ensureNode(s);
            ensureNode(o);

            edges.push({
                data: { id: `e${i}`, source: s, target: o, label: shorten(p) },
            });
        });

        const elements = [...nodesMap.values(), ...edges];

        const cy = cytoscape({
            container: cyContainerRef.current,
            elements,
            style: [
                { selector: "node", style: { label: "data(label)", "font-size": 10, "text-wrap": "wrap", "text-max-width": 120 } },
                { selector: "edge", style: { label: "data(label)", "font-size": 8, "curve-style": "bezier", "target-arrow-shape": "triangle" } },
            ],
            layout: { name: "cose" },
        });

        cyInstanceRef.current = cy;
        return () => cy.destroy();
    }, [tab, graph.data, articleUri]);

    const topicRows = bindings(topics.data);
    const mediaRows = bindings(media.data);

    return (
        <div style={{ maxWidth: 1100, margin: "0 auto", padding: 24 }}>
            <p>
                <Link to="/">Search</Link> / <Link to="/results">Results</Link>
            </p>

            <div style={{ display: "flex", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
                <div>
                    <h2 style={{ marginBottom: 4 }}>{title ?? "Article"}</h2>
                    <div style={{ fontSize: 12, color: "#444" }}>{articleUri}</div>
                </div>
                <div style={{ textAlign: "center" }}>
                    <QRCodeCanvas value={window.location.href} size={92} />
                    <div style={{ fontSize: 11, color: "#444" }}>QR (article URL)</div>
                </div>
            </div>

            <div style={{ display: "flex", gap: 8, marginTop: 14, marginBottom: 16 }}>
                <button onClick={() => setTab("overview")}>Overview</button>
                <button onClick={() => setTab("provenance")}>Provenance</button>
                <button onClick={() => setTab("recommend")}>Recommend</button>
                <button onClick={() => setTab("graph")}>Visualize</button>
            </div>

            {tab === "overview" && (
                // RDFa + schema.org: bifează cerința HTML5 + RDFa constructs
                <article
                    vocab="http://schema.org/"
                    typeof="NewsArticle"
                    resource={articleUri}
                    style={{ border: "1px solid #ddd", borderRadius: 8, padding: 12 }}
                >
                    <h3 property="headline" style={{ marginTop: 0 }}>
                        {title ?? "(no title)"}
                    </h3>

                    {description && <p property="description">{description}</p>}

                    {language && <meta property="inLanguage" content={language} />}
                    {wordCount && <meta property="wordCount" content={wordCount} />}
                    {published && <meta property="datePublished" content={published} />}
                    {genre && <meta property="genre" content={genre} />}

                    {url && (
                        <p>
                            Source:{" "}
                            <a property="url" href={url} target="_blank" rel="noreferrer">
                                {url}
                            </a>
                        </p>
                    )}

                    {authorUri && (
                        <div property="author" typeof="Person" resource={authorUri}>
                            Author: <span property="name">{authorName ?? shorten(authorUri)}</span>
                        </div>
                    )}

                    <hr />

                    <h4>Topics (SKOS / Thesaurus)</h4>
                    <ul>
                        {topicRows.map((b, i) => {
                            const concept = v(b, "concept");
                            const label = v(b, "label");
                            const subject = v(b, "subject");
                            const txt = label ?? subject ?? (concept ? shorten(concept) : null);
                            if (!txt) return null;
                            return <li key={i}>{txt}</li>;
                        })}
                    </ul>

                    <h4>Media</h4>
                    <ul>
                        {mediaRows.map((b, i) => {
                            const contentUrl = v(b, "contentUrl");
                            const format = v(b, "format");
                            if (!contentUrl) return null;

                            const isAudio = (format || "").startsWith("audio/") || contentUrl.toLowerCase().endsWith(".mp3");
                            const isVideo = (format || "").startsWith("video/") || contentUrl.toLowerCase().endsWith(".mp4");
                            const isImage = (format || "").startsWith("image/") || contentUrl.match(/\.(png|jpg|jpeg)$/i);

                            return (
                                <li key={i}>
                                    {isImage && <img src={contentUrl} alt="media" style={{ maxWidth: 520, display: "block" }} />}
                                    {isAudio && <audio controls src={contentUrl} />}
                                    {isVideo && <video controls src={contentUrl} style={{ maxWidth: 640 }} />}
                                    {!isAudio && !isVideo && !isImage && (
                                        <a href={contentUrl} target="_blank" rel="noreferrer">{contentUrl}</a>
                                    )}
                                    {format && <div style={{ fontSize: 12, color: "#444" }}>{format}</div>}
                                </li>
                            );
                        })}
                    </ul>

                    {body && (
                        <>
                            <hr />
                            <h4>Content</h4>
                            <div property="articleBody" style={{ whiteSpace: "pre-wrap" }}>{body}</div>
                        </>
                    )}

                    {overview.isLoading && <p>Loading…</p>}
                </article>
            )}

            {tab === "provenance" && (
                <section style={{ border: "1px solid #ddd", borderRadius: 8, padding: 12 }}>
                    <h3 style={{ marginTop: 0 }}>Provenance (PROV-O)</h3>
                    {prov.isLoading && <p>Loading…</p>}
                    <ul>
                        {bindings(prov.data).map((b, i) => (
                            <li key={i}>
                                activity: {v(b, "activity") ?? "-"}<br />
                                used: {v(b, "used") ?? "-"}<br />
                                when: {v(b, "when") ?? "-"}<br />
                                agent: {v(b, "agentName") ?? "-"}
                            </li>
                        ))}
                    </ul>
                </section>
            )}

            {tab === "recommend" && (
                <section style={{ border: "1px solid #ddd", borderRadius: 8, padding: 12 }}>
                    <h3 style={{ marginTop: 0 }}>Recommendations</h3>
                    {rec.isLoading && <p>Loading…</p>}
                    <ul>
                        {bindings(rec.data).map((b, i) => {
                            const other = v(b, "other");
                            const t = v(b, "title");
                            const score = v(b, "score");
                            if (!other) return null;
                            return (
                                <li key={i}>
                                    <Link to={`/article?uri=${encodeURIComponent(other)}`}>{t ?? shorten(other)}</Link>
                                    {score && <span style={{ marginLeft: 8, fontSize: 12, color: "#444" }}>score: {score}</span>}
                                </li>
                            );
                        })}
                    </ul>
                </section>
            )}

            {tab === "graph" && (
                <section style={{ border: "1px solid #ddd", borderRadius: 8, padding: 12 }}>
                    <h3 style={{ marginTop: 0 }}>Graph visualization</h3>
                    {graph.isLoading && <p>Loading…</p>}
                    <div ref={cyContainerRef} style={{ width: "100%", height: 520, border: "1px solid #eee" }} />
                    <p style={{ fontSize: 12, color: "#444" }}>
                        Vizualizare din triple SPARQL (1–2 hop-uri în jurul articolului).
                    </p>
                </section>
            )}
        </div>
    );
}
