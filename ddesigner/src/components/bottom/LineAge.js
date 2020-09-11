import React, { Component } from 'react';
import { translate } from 'react-i18next'
import { Tree, Row, Col, Tabs } from 'antd';
import resource from "./../../Resource";
import PropTypes from 'prop-types';
import { classExtension } from '../classExtension.js';

const TreeNode = Tree.TreeNode
const TabPane = Tabs.TabPane

class LineAge extends Component {

    static propTypes = {
        entity: PropTypes.object,
        cellEntity: PropTypes.object
    }

    constructor(...args) {
        super(...args);
        this.state = {
            selectedNode: null,
            newSelectedNode: null,
            selectedNodeData: null,
            valueList: [],
            selectedLeaf: null,
            selectedListData: [],
            selectedPortKey: []
        }
        this.depKeys = []
        this.folKeys = []
    }

    getTreeNode(el, type, parent) {
        if (el[type]) {
            if (this.state.selectedNode.name !== el.nodeName) {
                var key = parent + el.nodeName + el.fieldName
                type === 'dependencies' ? this.depKeys.push(key) : this.folKeys.push(key)
                return (
                    <TreeNode key={parent + el.nodeName + el.fieldName} title={el.nodeName + '.' + el.fieldName + ':' + el.fieldType} fieldName={el.fieldName} data={el.nodeName}>
                        {el[type].map((f, index) => this.getTreeNode(f, type, parent))}
                    </TreeNode>
                )
            } else {
                return el[type].map((f, index) => this.getTreeNode(f, type, parent))
            }
        }
    }

    getList(selectedNodeData) {
        let valueList = []
        if (selectedNodeData) {
            if (selectedNodeData.dependencies) {
                valueList = selectedNodeData.dependencies.map((el, index) =>
                    <TreeNode key={el.fieldName + index} title={el.fieldName + ': ' + el.fieldType} data={el.fieldName}></TreeNode>
                )
            } else {
                if (selectedNodeData.followers) {
                    valueList = selectedNodeData.followers.map((el, index) =>
                        <TreeNode key={el.fieldName + index} title={el.fieldName + ': ' + el.fieldType} data={el.fieldName}></TreeNode>
                    )
                }
            }
        }
        this.setState({ valueList })
    }

    treeListOnClick(event) {
        this.depKeys = []
        this.folKeys = []
        this.setState({ selectedLeaf: event.node.props.data, selectedListData: event.node.props.data })
    }

    portInTreeOnClick(event) {
        const { context } = this.props
        if (context.selectNodeInDesigner) {
            const entity = classExtension.nodes(context.entity).find(n => n.name === event.node.props.data)
            context.selectNodeInDesigner(entity)
            this.setState({ selectedLeaf: event.node.props.fieldName })
        }
    }

    portOutTreeOnClick(event) {
        const { context } = this.props
        if (context.selectNodeInDesigner) {
            const entity = classExtension.nodes(context.entity).find(n => n.name === event.node.props.data)
            context.selectNodeInDesigner(entity)
            this.setState({ selectedLeaf: event.node.props.fieldName })
        }
    }

    createDependenciesView() {
        const { selectedNodeData, valueList, selectedLeaf, selectedListData, selectedPortKey } = this.state
        const { t } = this.props
        const dependencies = selectedNodeData ?
            selectedNodeData.dependencies.filter(f => f.fieldName === selectedLeaf).map(dep => this.getTreeNode(dep, 'dependencies', selectedListData + 'in_')) : []
        const followers = selectedNodeData ?
            selectedNodeData.followers.filter(f => f.fieldName === selectedLeaf).map(fl => this.getTreeNode(fl, 'followers', selectedListData + 'out_')) : []
        const selectedTreeNode = this.state.valueList.length > 0 && selectedLeaf ? this.state.valueList.find(v => v.props.data === selectedLeaf) : []

        if (selectedNodeData) {
            return (
                <Row >
                    <Col span={5}>
                        <div style={{ height: '50vh', overflow: 'auto' }}>
                            {valueList.length !== 0 &&
                                <Tree onSelect={(key, event) => this.treeListOnClick(event)} selectedKeys={selectedTreeNode ? [selectedTreeNode.key] : []}>
                                    {valueList}
                                </Tree>
                            }
                        </div>
                    </Col>
                    <Col span={19}>
                        <div style={{ height: '50vh', overflow: 'auto' }}>
                            <Tabs type="card" showLine>
                                <TabPane tab={t('inport')} key={'1'}>
                                    {dependencies.length !== 0 &&
                                        <Tree expandedKeys={this.depKeys} selectedKeys={selectedPortKey} onSelect={(key, event) => this.portInTreeOnClick(event)}>
                                            {dependencies}
                                        </Tree>
                                    }
                                </TabPane>
                                <TabPane tab={t('outport')} key={'2'}>
                                    {followers.length !== 0 &&
                                        <Tree expandedKeys={this.folKeys} selectedKeys={selectedPortKey} onSelect={(key, event) => this.portOutTreeOnClick(event)}>
                                            {followers}
                                        </Tree>
                                    }
                                </TabPane>
                            </Tabs>
                        </div>
                    </Col>
                </Row>
            )
        }
    }

    getNodeDependencies() {
        if (this.state.selectedNode) {
            if (this.state.selectedNode.transformation) {
                resource.call({
                    _type_: "etl.Transformation",
                    e_id: this.state.selectedNode.transformation.e_id,
                    node_name: this.state.selectedNode.name,
                }, "nodeDependencies", {}).then(data => {
                    this.setState({ selectedNodeData: data }, () => {
                        this.getList(data)
                    })
                })
            }
        }
    }

    componentDidMount() {
        if (this.state.selectedNode !== this.state.newSelectedNode) {
            this.setState({ selectedNode: this.state.newSelectedNode }, () => {
                this.getNodeDependencies()
                this.setState({ selectedPortKey: [] })
            })
        }
    }

    componentDidUpdate(prevProps, prevState, snapshot) {
        if (this.state.selectedNode !== this.state.newSelectedNode) {
            this.setState({ selectedNode: this.state.newSelectedNode }, () => {
                this.getNodeDependencies()
                this.setState({ selectedPortKey: [] })
            })
        }
    }

    static getDerivedStateFromProps(props, state) {
        if (props.context.selectedNode && state) {
            if (props.context.selectedNode !== state.selectedNode) {
                return { newSelectedNode: props.context.selectedNode }
            } else {
                return null
            }
        }
        return null
    }

    render() {
        return (
            <div>
                {this.props.activeObject && this.createDependenciesView()}
            </div>
        )
    }
}

export default translate()(LineAge);
