<!--
  Dynamic sidebar fragment.

  Usage in admin-dashboard.html / user-dashboard.html:
  Replace the whole hand-coded <div class="sidebar-inner">...</div> block with:

      <div class="sidebar-inner"
           th:replace="~{fragments/sidebar :: sidebar-items}"></div>

  "sidebarMenu" is injected automatically on every request by
  MenuControllerAdvice, so no controller changes are needed.
-->
<div th:fragment="sidebar-items">

    <div class="nav-item" th:each="item : ${sidebarMenu}">

        <!-- Leaf item: no children -> direct link/button -->
        <button th:if="${#lists.isEmpty(item.children)}"
                class="nav-btn"
                th:classappend="${item.moduleCode == 'DASHBOARD'} ? ' active' : ''"
                th:id="${item.moduleCode == 'DASHBOARD'} ? 'dashMenuHeader' : null"
                th:attr="data-url=${item.moduleUrl}, data-code=${item.moduleCode}"
                onclick="handleMenuClick(event, this)">
            <div class="nav-btn-inner">
                <div class="nav-icon"><i th:class="'bi ' + ${item.icon}"></i></div>
                <span th:text="${item.moduleName}"></span>
            </div>
        </button>

        <!-- Section header: has children -> expandable submenu -->
        <th:block th:unless="${#lists.isEmpty(item.children)}">
            <button class="nav-btn" th:attr="onclick=|toggleSub('${item.moduleCode}', this)|">
                <div class="nav-btn-inner">
                    <div class="nav-icon"><i th:class="'bi ' + ${item.icon}"></i></div>
                    <span th:text="${item.moduleName}"></span>
                </div>
                <i class="bi bi-chevron-right arrow"></i>
            </button>
            <div class="submenu" th:id="'submenu-' + ${item.moduleCode}">
                <!-- If this section has a direct URL, add it as the first item -->
                <a th:if="${item.moduleUrl != null && !item.moduleUrl.isEmpty()}"
                   href="#"
                   class="submenu-link"
                   th:attr="data-url=${item.moduleUrl}, data-code=${item.moduleCode}"
                   onclick="handleMenuClick(event, this)">
                    <i th:class="'bi ' + ${item.icon}"></i>
                    <span th:text="${item.moduleName}"></span>
                </a>
                <!-- Then list all children -->
                <a href="#" class="submenu-link" th:each="child : ${item.children}"
                   th:attr="data-url=${child.moduleUrl}, data-code=${child.moduleCode}"
                   onclick="handleMenuClick(event, this)">
                    <i th:class="'bi ' + ${child.icon}"></i>
                    <span th:text="${child.moduleName}"></span>
                </a>
            </div>
        </th:block>

    </div>
</div>