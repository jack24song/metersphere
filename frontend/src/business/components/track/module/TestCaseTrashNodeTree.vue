<template>
  <ms-node-tree class="node-tree"
           v-loading="result.loading"
           local-suffix="test_case"
           default-label="未规划用例"
           @nodeSelectEvent="publicNodeChange"
           :tree-nodes="trashTreeNodes"
           ref="trashNodeTree"/>
</template>

<script>
import MsNodeTree from "@/business/components/track/common/NodeTree";
import {getTestCaseTrashNodes} from "@/network/testCase";
export default {
  name: "TestCaseTrashNodeTree",
  components: {MsNodeTree},
  props: {
    caseCondition: Object
  },
  data() {
    return {
      trashTreeNodes: [],
      result: {}
    }
  },
  methods: {
    publicNodeChange(node, nodeIds, pNodes) {
      this.$emit("nodeSelectEvent", node, node.data.id === 'root' ? [] : nodeIds, pNodes);
    },
    list() {
      this.result = getTestCaseTrashNodes(this.caseCondition, data => {
        this.trashTreeNodes = data;
        if (this.$refs.trashNodeTree) {
          this.trashTreeNodes.forEach(firstLevel => {
              this.$refs.trashNodeTree.nodeExpand(firstLevel);
          })
        }
      });
    },
  }
}
</script>

<style scoped>

</style>
