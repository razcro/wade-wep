export function v(binding, key) {
    return binding && binding[key] ? binding[key].value : null;
}

export function bindings(results) {
    return results && results.results && Array.isArray(results.results.bindings)
        ? results.results.bindings
        : [];
}
