import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { defaultSearch } from "../models/search";

export default function SearchPage() {
    const [req, setReq] = useState({ ...defaultSearch });
    const nav = useNavigate();

    function submit(e) {
        e.preventDefault();
        const p = new URLSearchParams();
        if (req.query) p.set("q", req.query);
        if (req.language) p.set("lang", req.language);
        if (req.mediaType) p.set("type", req.mediaType);
        if (req.maxWords != null) p.set("maxWords", String(req.maxWords));
        if (req.dateFrom) p.set("from", req.dateFrom);
        if (req.dateTo) p.set("to", req.dateTo);
        nav(`/results?${p.toString()}`);
    }

    return (
        <div className="container">
            <div className="header">
                <div>
                    <h1 className="h-title">NewsProvenience</h1>
                    <p className="h-sub">
                        Semantic search over the knowledge graph (SPARQL). Explore provenance, topics (SKOS), multimedia and recommendations.
                    </p>
                </div>
            </div>

            <div className="card">
                <form onSubmit={submit} className="grid">
                    <div>
                        <span className="label">Query</span>
                        <input
                            className="input"
                            placeholder="technology, IT contest, investigative journalism..."
                            value={req.query}
                            onChange={(e) => setReq((p) => ({ ...p, query: e.target.value }))}
                        />
                    </div>

                    <div className="row">
                        <div>
                            <span className="label">Language</span>
                            <input
                                className="input"
                                placeholder="en / ro / es"
                                value={req.language}
                                onChange={(e) => setReq((p) => ({ ...p, language: e.target.value }))}
                            />
                        </div>

                        <div>
                            <span className="label">Type</span>
                            <input
                                className="input"
                                placeholder="editorial / investigation / documentary"
                                value={req.mediaType}
                                onChange={(e) => setReq((p) => ({ ...p, mediaType: e.target.value }))}
                            />
                        </div>

                        <div>
                            <span className="label">Max words</span>
                            <input
                                className="input"
                                type="number"
                                value={req.maxWords}
                                onChange={(e) => setReq((p) => ({ ...p, maxWords: Number(e.target.value) }))}
                            />
                        </div>

                        <div style={{ display: "flex", alignItems: "flex-end" }}>
                            <button className="btn" type="submit" style={{ width: "100%" }}>
                                Search
                            </button>
                        </div>
                    </div>

                    <div className="row-2">
                        <div>
                            <span className="label">From</span>
                            <input
                                className="input"
                                type="date"
                                value={req.dateFrom}
                                onChange={(e) => setReq((p) => ({ ...p, dateFrom: e.target.value }))}
                            />
                        </div>
                        <div>
                            <span className="label">To</span>
                            <input
                                className="input"
                                type="date"
                                value={req.dateTo}
                                onChange={(e) => setReq((p) => ({ ...p, dateTo: e.target.value }))}
                            />
                        </div>
                    </div>

                    <div className="kpis">
                        <span className="pill">SPARQL-driven</span>
                        <span className="pill">RDFa + schema.org</span>
                        <span className="pill">PROV-O provenance</span>
                        <span className="pill">SKOS concepts</span>
                        <span className="pill">MediaObjects</span>
                    </div>
                </form>
            </div>

            <p className="small" style={{ marginTop: 12 }}>
                Tip: încearcă lang=en, maxWords=4000, type=editorial.
            </p>
        </div>
    );
}
