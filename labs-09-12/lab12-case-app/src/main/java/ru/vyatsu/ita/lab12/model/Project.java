package ru.vyatsu.ita.lab12.model; public record Project(long id,String name,String rootPath,String language) { public String toString(){return name+" ["+language+"]";} }
