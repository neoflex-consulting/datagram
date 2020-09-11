import React, { Component } from 'react';
import { Tree, Input, Avatar } from 'antd';
import resource from '../Resource'
import { translate } from 'react-i18next'
import _ from 'lodash'
import reactStringReplace from 'react-string-replace';
import { getIcon } from './../utils/meta'

const TreeNode = Tree.TreeNode;
const Search = Input.Search;

class ObjectExplorer extends Component {

    constructor() {
        super()
        this.state = { tree: { e_id: null, _type_: 'ui3.Module', name: 'Loading...', uid: '', children: [] }, filterStr: "", expandedKeys: [] }
        this.gotFocus = false
    }

    componentDidUpdate() {
        if (this.filterInput && !this.gotFocus) {
            this.filterInput.focus()
            this.gotFocus = true
        }
    }

    componentDidMount() {
        resource.query('/api/operation/MetaServer/utils/ObjectExplorer/getRootNode').then(tree => {
            this.setState({ tree })
        })
    }

    getTitle(node) {
        return <span><Avatar src={getIcon(node)} shape={"square"} size={"small"} />&nbsp; {reactStringReplace(node.name, this.state.filterStr, (match, i) => (
            <span key={i} style={{ color: 'red' }}>{match}</span>
        ))}</span>
    }

    renderNode(node) {
        return (
            node.children.length > 0 ?
                <TreeNode title={this.getTitle(node)} key={node.uid}>
                    {node.children.map(child => this.renderNode(child))}
                </TreeNode> : <TreeNode title={this.getTitle(node)} key={node.uid} />
        )
    }

    matchNode(node, filterStr) {
        if (this.matchName(node, filterStr)) {
            return true
        }
        return this.matchNodes(node.children, filterStr)
    }
    matchNodes(nodes, filterStr) {
        for (let node of nodes) {
            if (this.matchNode(node, filterStr)) {
                return true
            }
        }
        return false
    }
    collectExpandedKey(node, filterStr) {
        if (this.matchNodes(node.children, filterStr)) {
            return [node.uid, ..._.flatMap(node.children, child => this.collectExpandedKey(child, filterStr))]
        }
        return []
    }

    filterNodes(nodes, filterStr) {
        return nodes.map(child => this.filterNode(child, filterStr)).filter(node => !!node)
    }

    matchName(node, filterStr) {
        return node.name.toUpperCase().indexOf(this.state.filterStr) >= 0
    }
    filterNode(node, filterStr) {
        const match = this.matchName(node, filterStr)
        if (match) {
            return node
        }
        const children = this.filterNodes(node.children, filterStr)
        if (children.length === 0) {
            return undefined
        }
        return Object.assign({}, node, { children })
    }

    findObject(root, key) {
        if (root.uid === key) {
            return root
        }
        for (let child of root.children) {
            const found = this.findObject(child, key)
            if (!!found) {
                return found
            }
        }
        return undefined
    }

    findParent(root, key) {
        for (let child of root.children) {
            if (child.uid === key) {
                return root
            }
            const found = this.findParent(child, key)
            if (!!found) {
                return found
            }
        }
        return undefined
    }

    findPath(root, key) {
        const path = []
        while (true) {
            const parent = this.findParent(root, key)
            if (!parent) {
                return path.reverse()
            }
            path.push(parent)
            key = parent.uid
        }
    }
    buildPath(root, object) {
        return [...this.findPath(root, object.uid), object].map(entity => {
            const { _type_, name, e_id } = entity
            return { _type_, name, e_id }
        })
    }

    render() {
        //const {t} = this.props
        return (
            <div>
                <Search style={{ marginBottom: 8 }}
                    onChange={e => {
                        const filterStr = e.target.value.toUpperCase()
                        const expandedKeys = filterStr.length === 0 ? [] : _.flatMap(this.state.tree.children, child => this.collectExpandedKey(child, filterStr))
                        this.setState({ filterStr, expandedKeys })
                    }}
                    ref={filterInput => this.filterInput = filterInput}
                />
                <div>
                    <Tree showLine autoExpandParent={false}
                        onSelect={(selectedKeys, info) => {
                            if (selectedKeys.length > 0) {
                                const selected = this.findObject(this.state.tree, selectedKeys[0])
                                if (selected) {
                                    this.props.selectObject(this.buildPath(this.state.tree, selected))
                                }
                            }
                        }}
                        onExpand={(expandedKeys, info) => {
                            this.setState({ expandedKeys: expandedKeys })
                        }}
                        expandedKeys={this.state.expandedKeys}
                    >
                        {this.filterNodes(this.state.tree.children, this.state.filterStr).map(child => this.renderNode(child))}
                    </Tree>
                </div>
            </div>
        )
    }
}

export default translate()(ObjectExplorer);
