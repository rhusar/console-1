/*
 *  Copyright 2022 Red Hat
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.jboss.hal.ballroom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.elemento.Elements;
import org.jboss.elemento.IsElement;
import org.jboss.hal.resources.UIConstants;

import elemental2.dom.Element;
import elemental2.dom.HTMLElement;
import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsType;

import static java.util.Arrays.asList;
import static jsinterop.annotations.JsPackage.GLOBAL;
import static org.jboss.elemento.Elements.a;
import static org.jboss.elemento.Elements.div;
import static org.jboss.elemento.Elements.li;
import static org.jboss.elemento.Elements.ul;
import static org.jboss.elemento.EventType.click;
import static org.jboss.hal.resources.CSS.*;

public class Tabs implements IsElement<HTMLElement> {

    private final HTMLElement root;
    private final HTMLElement tabs;
    private final HTMLElement panes;
    private final Map<Integer, String> indexToId;
    private final Map<String, HTMLElement> paneElements;

    public Tabs(String id) {
        root = div().id(id)
                .add(tabs = ul()
                        .css(nav, navTabs, navTabsPf, navTabsHal)
                        .attr(UIConstants.ROLE, UIConstants.TABLIST).element())
                .add(panes = div().css(tabContent).element()).element();

        indexToId = new HashMap<>();
        paneElements = new HashMap<>();
    }

    @Override
    public HTMLElement element() {
        return root;
    }

    public Tabs add(String id, String title, HTMLElement first, HTMLElement... rest) {
        return add(id, title, elements(first, rest));
    }

    public Tabs add(String id, String title, Iterable<HTMLElement> elements) {
        int size = (int) tabs.childElementCount;
        if (size != (int) panes.childElementCount) {
            throw new IllegalStateException(
                    "Unbalanced containers: tabs(" + size + ") != panes(" + panes.childElementCount + ")");
        }
        indexToId.put(size, id);

        HTMLElement tab = li().attr(UIConstants.ROLE, "presentation") // NON-NLS
                .add(a("#" + id)
                        .aria(UIConstants.CONTROLS, id)
                        .attr(UIConstants.ROLE, UIConstants.TAB)
                        .data(UIConstants.TOGGLE, UIConstants.TAB)
                        .on(click, event -> {
                            event.preventDefault();
                            showTab(id);
                        })
                        .textContent(title))
                .element();
        HTMLElement pane = div().id(id).css(tabPane).attr(UIConstants.ROLE, "tabpanel").element(); // NON-NLS

        tabs.appendChild(tab);
        panes.appendChild(pane);
        paneElements.put(id, pane);
        if (tabs.childNodes.getLength() == 1) {
            tab.classList.add(active);
        }
        if (panes.childNodes.getLength() == 1) {
            pane.classList.add(active);
        }
        fillPane(pane, elements);

        return this;
    }

    private List<HTMLElement> elements(HTMLElement first, HTMLElement... rest) {
        List<HTMLElement> elements = new ArrayList<>();
        elements.add(first);
        if (rest != null) {
            elements.addAll(asList(rest));
        }
        return elements;
    }

    private void fillPane(HTMLElement pane, Iterable<HTMLElement> elements) {
        for (HTMLElement element : elements) {
            pane.appendChild(element);
        }
    }

    public void showTab(int index) {
        showTab(indexToId.get(index));
    }

    @SuppressWarnings("WeakerAccess")
    public void showTab(String id) {
        if (id != null) {
            Api.select("a[href='#" + id + "']").tab("show"); // NON-NLS
        }
    }

    public void setContent(int index, HTMLElement first, HTMLElement... rest) {
        setContent(indexToId.get(index), first, rest);
    }

    public void setContent(String id, HTMLElement first, HTMLElement... rest) {
        if (id != null) {
            HTMLElement pane = paneElements.get(id);
            if (pane != null) {
                Elements.removeChildrenFrom(pane);
                fillPane(pane, elements(first, rest));
            }
        }
    }

    public void onShow(String id, JsCallback callback) {
        if (id != null) {
            Api.select("a[href='#" + id + "']").on("shown.bs.tab", callback); // NON-NLS
        }
    }

    public HTMLElement tabElement(String id) {
        HTMLElement selectedTab = null;
        if (id != null) {
            selectedTab = (HTMLElement) tabs.querySelector("li > a[href='#" + id + "']"); // NON-NLS
        }
        return selectedTab;
    }

    public String getSelectedId() {
        Element a = tabs.querySelector("li." + active + " > a");
        if (a != null) {
            return a.getAttribute("aria-controls");
        }
        return null;
    }

    @JsType(isNative = true)
    static class Api {

        @JsMethod(namespace = GLOBAL, name = "$")
        public static native Api select(String selector);

        public native void tab(String command);

        public native void on(String event, JsCallback callback);
    }
}
