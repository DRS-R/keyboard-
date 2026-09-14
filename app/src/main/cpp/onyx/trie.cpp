#include "trie.h"

namespace onyx {

int Trie::new_node() {
    nodes_.emplace_back();
    return static_cast<int>(nodes_.size()) - 1;
}

void Trie::clear() { nodes_.clear(); }

void Trie::insert(const std::u32string& key, int entry_idx) {
    if (nodes_.empty()) new_node();
    int cur = 0;
    for (const char32_t c : key) {
        const auto it = nodes_[cur].children.find(c);
        if (it == nodes_[cur].children.end()) {
            const int nxt = new_node();
            nodes_[cur].children.emplace(c, nxt);
            cur = nxt;
        } else {
            cur = it->second;
        }
    }
    nodes_[cur].entries.push_back(entry_idx);
}

int Trie::walk(const std::u32string& key) const {
    if (nodes_.empty()) return -1;
    int cur = 0;
    for (const char32_t c : key) {
        const auto it = nodes_[cur].children.find(c);
        if (it == nodes_[cur].children.end()) return -1;
        cur = it->second;
    }
    return cur;
}

bool Trie::has_exact(const std::u32string& key) const {
    const int n = walk(key);
    return n >= 0 && !nodes_[n].entries.empty();
}

void Trie::collect_prefix(const std::u32string& prefix, int limit, std::vector<int>& out) const {
    const int n = walk(prefix);
    if (n < 0) return;
    dfs(n, limit, out);
}

void Trie::dfs(int node, int limit, std::vector<int>& out) const {
    if (static_cast<int>(out.size()) >= limit) return;
    for (const int idx : nodes_[node].entries) {
        out.push_back(idx);
        if (static_cast<int>(out.size()) >= limit) return;
    }
    for (const auto& [c, child] : nodes_[node].children) {
        dfs(child, limit, out);
        if (static_cast<int>(out.size()) >= limit) return;
    }
}

} // namespace onyx
