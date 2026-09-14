// Onyx Trie — خوارزمية 46: بادئات فورية O(طول الكلمة) فوق نقاط الكود
#pragma once
#include <string>
#include <vector>
#include <unordered_map>

namespace onyx {

class Trie {
public:
    void insert(const std::u32string& key, int entry_idx);
    bool has_exact(const std::u32string& key) const;
    // يجمع فهارس المدخلات تحت بادئة مع حد أقصى
    void collect_prefix(const std::u32string& prefix, int limit, std::vector<int>& out) const;
    void clear();
private:
    struct Node {
        std::unordered_map<char32_t, int> children; // حرف → فهرس العقدة
        std::vector<int> entries;                   // مدخلات تنتهي هنا
    };
    std::vector<Node> nodes_;
    int new_node();
    int walk(const std::u32string& key) const; // فهرس عقدة النهاية أو -1
    void dfs(int node, int limit, std::vector<int>& out) const;
};

} // namespace onyx
