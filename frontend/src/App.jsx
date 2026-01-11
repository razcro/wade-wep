import { BrowserRouter, Routes, Route } from "react-router-dom";
import SearchPage from "./pages/SearchPage";
import ResultsPage from "./pages/ResultsPage";
import ArticlePage from "./pages/ArticlePage";

export default function App() {
    return (
        <BrowserRouter>
            <Routes>
                <Route path="/" element={<SearchPage />} />
                <Route path="/results" element={<ResultsPage />} />
                <Route path="/article" element={<ArticlePage />} />
            </Routes>
        </BrowserRouter>
    );
}
