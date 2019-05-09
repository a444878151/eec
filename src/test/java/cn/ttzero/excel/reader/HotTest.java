/*
 * Copyright (c) 2019, guanquan.wang@yandex.com All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.ttzero.excel.reader;

import org.junit.Test;

import java.util.Iterator;

import static cn.ttzero.excel.Print.println;

/**
 * Create by guanquan.wang at 2019-05-07 15:17
 */
public class HotTest {
    @Test public void testPut1() {
        Hot<Integer, String> hot = new Hot<>();
        hot.push(1, "a");
        hot.push(2, "b");
        hot.push(3, "c");
        hot.push(4, "d");

        assert hot.size() == 4;
        assert hot.get(2).equals("b");

        hot.forEach(e -> println(e.getK() + ": " + e.getV()));

        println(hot.get(3));
    }

    @Test public void testPut2() {
        Hot<String, Integer> hot = new Hot<>();
        hot.push("a", 1);
        hot.push("b", 2);
        hot.push("c", 3);
        hot.push("d", 4);
        hot.push("e", 5);

        assert hot.size() == 5;
        assert hot.get("c") == 3;

        hot.forEach(e -> println(e.getK() + ": " + e.getV()));
    }

    @Test public void testIterator() {
        Hot<String, Integer> hot = new Hot<>();
        hot.push("a", 1);
        hot.push("b", 2);
        hot.push("c", 3);
        hot.push("d", 4);
        hot.push("e", 5);

        for (Iterator<Hot.E<String, Integer>> ite = hot.iterator(); ite.hasNext();) {
            Hot.E<String, Integer> e = ite.next();
            println(e.getK() + ": " + e.getV());
        }
    }

    @Test public void testRemove() {
        Hot<Integer, String> hot = new Hot<>();
        hot.push(1, "a");
        hot.push(2, "b");
        hot.push(3, "c");
        hot.push(4, "d");

        assert hot.size() == 4;
        hot.forEach(e -> println(e.getK() + ": " + e.getV()));

        hot.remove();

        assert hot.size() == 3;

        hot.remove();
        hot.forEach(e -> println(e.getK() + ": " + e.getV()));
        hot.remove();
        hot.forEach(e -> println(e.getK() + ": " + e.getV()));
        hot.remove();
        hot.forEach(e -> println(e.getK() + ": " + e.getV()));
        hot.remove();
        hot.remove();
        hot.remove();
        hot.remove();
        hot.forEach(e -> println(e.getK() + ": " + e.getV()));
    }

    @Test public void testClear() {
        Hot<Integer, String> hot = new Hot<>();
        hot.push(1, "a");
        hot.push(2, "b");
        hot.push(3, "c");
        hot.push(4, "d");

        assert hot.size() == 4;
        hot.clear();

        assert hot.size() == 0;
        hot.forEach(e -> println(e.getK() + ": " + e.getV()));
    }
}
