import React, {Component} from 'react';
import {translate} from 'react-i18next'
import { Tree } from 'antd';
import _ from 'lodash';

const TreeNode = Tree.TreeNode;

class PortsTree extends Component {

    renderField(field, alias, isDisableSorting, curPath, index) {
        const fieldType = field.domainStructure ? (field.domainStructure._type_ === "dataset.ArrayType" ? "ARRAY" : "STRUCT") : field.dataTypeDomain

        let newPath = curPath + "^" + field.name + index;

        let value =
            (<div>
                {(field.name + (fieldType ? ' ' + fieldType : ''))}
                {
                    this.props.renderActions &&
                        this.props.renderActions(newPath)
                }
                </div>
            );

        return (<TreeNode
            title={value} key={alias + "-" + field.name} data={field.name} field={field}>
            {field.domainStructure &&
                    this.renderFields(field.domainStructure, alias, isDisableSorting, newPath)
                }
            </TreeNode>)
    }

    renderFields(fields, alias, isDisableSorting, curPath) {
        if(fields.internalStructure) {
            return this.renderFields(fields.internalStructure.fields, alias + "internalStructure", isDisableSorting, curPath)
        }
        if(fields.elementType) {
            return this.renderFields(fields.elementType, alias + "Array", isDisableSorting, curPath)
        }

        if (isDisableSorting) {
            return _.map(fields, (field, index) => this.renderField(field, alias, isDisableSorting, curPath, index))

        } else {
            return _.sortBy(fields, [o => o.name])
                .map((field, index) => this.renderField(field, alias, isDisableSorting, curPath, index))
        }
    }

    getFieldAlias(field) {
        var alias = undefined;
        if(field && field.dataSet) {
            if(this.props.getAlias) {
                alias = this.props.getAlias(field.dataSet)
            }
        }
        return (alias || this.props.defaultAlias) || "";
    }

    getFieldKey(field) {
        return this.getFieldAlias(field) + '-' + field.name
    }

    createTreePane() {
        const { ports, isDisableSorting } = this.props

        return <div>
            {ports &&
                <Tree
                    showLine
                    onSelect={(key, event) => this.props.treeOnClick(event)}
                    defaultSelectedKeys={this.props.selectedField ? [this.getFieldKey(this.props.selectedField)] : []}
                    defaultExpandedKeys={this.props.selectedField ? ['branch-' + this.getFieldAlias(this.props.selectedField)] : []}
                >
            {ports.filter(port=>port).map((port) => {
                let alias = port.alias
                if(this.props.getAlias) {
                    alias = this.props.getAlias(port)
                }

                let value =
                    (<div>
                            {(alias)}
                            {
                                this.props.renderGroupActions &&
                                this.props.renderGroupActions(alias)
                            }
                    </div>
                    );


                return (
                    <TreeNode title={value} key={'branch-' + alias} data={alias}>
                        {port.fields &&
                            this.renderFields(port.fields, alias, isDisableSorting, alias)}

                    </TreeNode>
                )
            })}
        </Tree>}
        </div>
    }

    render() {
        return this.createTreePane()
    }
}

export default translate()(PortsTree);
