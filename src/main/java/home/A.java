package home;

import java.util.LinkedList;
import java.util.List;

public class A {
    private Integer id;
    private String name;
    private List<B> bs = new LinkedList<>();

    public A() {
    }

    public A(Integer id, String name) {
        this.id = id;
        this.name = name;
    }

    public A add(B b) {
        bs.add(b);
        return this;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<B> getBs() {
        return bs;
    }
}
