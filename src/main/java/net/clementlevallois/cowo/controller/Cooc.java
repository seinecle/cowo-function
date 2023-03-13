/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package net.clementlevallois.cowo.controller;

import net.clementlevallois.umigon.model.NGram;

/**
 *
 * @author LEVALLOIS
 */
public class Cooc {

    NGram a;
    NGram b;

    public Cooc() {
    }

    public Cooc(NGram a, NGram b) {
        if (a.getOriginalFormLemmatized().compareToIgnoreCase(b.getOriginalFormLemmatized()) > 0) {
            this.a = a;
            this.b = b;
        } else {
            this.a = b;
            this.b = a;
        }
    }

    public NGram getA() {
        return a;
    }

    public void setA(NGram a) {
        this.a = a;
    }

    public NGram getB() {
        return b;
    }

    public void setB(NGram b) {
        this.b = b;
    }

    @Override
    public int hashCode() {
        int hashFirst = a.getOriginalFormLemmatized().hashCode();
        int hashSecond = b.getOriginalFormLemmatized().hashCode();
        int maxHash = Math.max(hashFirst, hashSecond);
        int minHash = Math.min(hashFirst, hashSecond);
        return minHash * 31 + maxHash;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (!(o instanceof Cooc)) {
            return false;
        }
        Cooc pairo = (Cooc) o;
        return ((this.a.getOriginalFormLemmatized().equalsIgnoreCase(pairo.a.getOriginalFormLemmatized())
                && this.b.getOriginalFormLemmatized().equalsIgnoreCase(pairo.b.getOriginalFormLemmatized())))
                || ((this.a.getOriginalFormLemmatized().equalsIgnoreCase(pairo.b.getOriginalFormLemmatized())
                && this.b.getOriginalFormLemmatized().equalsIgnoreCase(pairo.a.getOriginalFormLemmatized())));
    }

    @Override
    public String toString() {
        return new StringBuilder().append(a.getOriginalFormLemmatized()).append("{--}").append(b.getOriginalFormLemmatized()).toString();
    }

}
