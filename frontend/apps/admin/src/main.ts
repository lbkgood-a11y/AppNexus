import { createApp, h } from "vue";
import { createPinia } from "pinia";
import { ORGANIZATION_RESOURCE } from "@appnexus/organization";
import { createTenantSession } from "@appnexus/security";

const session = createTenantSession();
createApp({
  render: () => h("main", `${ORGANIZATION_RESOURCE}:${session.contextVersion}`)
}).use(createPinia()).mount("#app");
