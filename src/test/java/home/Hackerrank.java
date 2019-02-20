package home;

import org.junit.jupiter.api.Test;

public class Hackerrank {
    @Test
    /**
     * a { b c [ d ( e ) f ] } { g [ h (i { j } ) ] } k { l [ m ( n { o p } q ) r s ] t } u v x y z
     */
    public void test() {
        String s = "a{bc[d(e)f]}{g[h(i{j})]}k{l[m(n{op}q)rs]t}uvxyz";
        java.util.Stack<Character> stack = new java.util.Stack<>();
        int deepest = 0;
        java.util.List<String> result = new java.util.ArrayList<>();
        int current = 0;
        for (byte b : s.getBytes()) {
            Character c = (char)b;
            if (isOpen(c)) {
                current += 1;
                stack.push(c);
            }
            else if (isClose(c)) {
                if (current > deepest) {
                    deepest = current;
                    result.clear();
                    result.add(popUntilOpen(stack));
                    current -= 1;
                }
                else if (current == deepest) {
                    result.add(popUntilOpen(stack));
                    current -= 1;
                }
                else stack.push(c);
            }
            else stack.push(c);
        }
        for (String r : result) System.out.println(r);
    }

    private String popUntilOpen(java.util.Stack<Character> stack) {
        StringBuilder sb = new StringBuilder();
        while (true) {
            Character c = stack.pop();
            if (isOpen(c)) break;
            sb.append(c);
        }
        return sb.reverse().toString();
    }

    class C {
        int a;
        String s;
        C(int _a, String _s) {
            a = _a;
            s = _s;
        }
    }

    @Test
    public void test2() {
        String s = "a{bc[d(e)f]}{g[h(i{j})]}k{l[m(n{op}q)rs]t}uvxyz";
        StringBuilder sb = new StringBuilder();
        int indent = 0;
        for (byte b : s.getBytes()) {
            char c = (char)b;
            if (isOpen(c)) {
                indent += 1;
                sb.append(build(indent));
            }
            else if (isClose(c)) {
                indent -= 1;
                sb.append('\n');
            }
            else sb.append(c);
        }
        java.util.stream.Stream.of(sb.toString().split("\n"))
                .filter(x -> !x.equals(""))
                .map(this::toC)
                .collect(java.util.stream.Collectors.toMap(
                        c -> c.a,
                        c -> java.util.Collections.singleton(c.s),
                        (s1, s2) -> {
                            java.util.Set<String> set = new java.util.HashSet<>();
                            set.addAll(s1);
                            set.addAll(s2);
                            return set;
                        }
                ))
                .entrySet()
                .stream()
                .max(java.util.Comparator.comparing(java.util.Map.Entry::getKey))
                .map(java.util.Map.Entry::getValue)
                .orElse(new java.util.HashSet<>())
                .forEach(System.out::println);
    }

    private C toC(String s) {
        String x = s.trim();
        return new C(s.length() - x.length(), x);
    }

    private String build(int indent) {
        StringBuilder sb = new StringBuilder("\n");
        for (int i = 1; i <= indent; i++) sb.append(' ');
        return sb.toString();
    }

    private boolean isOpen(char b) {
        return b == '{' || b == '[' || b == '(';
    }

    private boolean isClose(char b) {
        return b == '}' || b == ']' || b == ')';
    }
}
