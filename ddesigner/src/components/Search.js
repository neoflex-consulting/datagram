import React, {Component} from 'react';
import {translate} from 'react-i18next'
import {Input, Table} from 'antd'
import reactStringReplace from 'react-string-replace'
import {getPaths} from '../utils/meta'
import _ from 'lodash'


function doFind(entity, filterStr) {
    const paths = new Map()
    getPaths(entity, paths, "")
    return _.sortBy(_.flatten([...paths.keys()].map(object=>{
        const path = paths.get(object)
        const result = []
        for (var propertyName in object) {
            if (object.hasOwnProperty(propertyName)) {
                const value = object[propertyName]
                if (Object.prototype.toString.call(value) !== '[object Object]' &&
                    value.toString().toLowerCase().indexOf(filterStr.toLowerCase()) > -1) {
                    result.push({path: `${path}.${propertyName}`, value: value.toString()})
                }
            }
        }
        return result
    })), e=>e.key)
}

class Search extends Component {
    constructor(...args) {
        super(...args)
        this.state = {
            filterStr: undefined,
            searchResults: [],
            selectedRowKeys: []
        }
    }

    doSearch(filterStr) {
        const {entity} = this.props.context
        if (entity && filterStr) {
            const searchResults = doFind(entity, filterStr)
            this.setState({filterStr, searchResults})
        }
        else {
            this.setState({filterStr, searchResults: []})
        }
    }

    selectNode(path) {
        const {context} = this.props
        if (path && context.selectNodeInDesigner) {
            const index = path.indexOf(".")
            if (index > 0) {
                const top = path.slice(0, index)
                const step = _.get(context.entity, top)
                if (step) {
                    context.selectNodeInDesigner(step)
                }
            }
        }
    }

    render() {
        const columns = [
            {
                title: 'Path',
                dataIndex: 'path',
                width: 80,
                render: (text)=><span>{text}</span>,
            },
            {
                title: 'Value',
                dataIndex: 'value',
                width: 150,
                render: (text)=>reactStringReplace(text, this.state.filterStr,  (match, i) => (
                    <span key={i} style={{ color: 'red' }}>{match}</span>
                ))
            },
        ]
        return (
            <div>
                <Input.Search style={{marginBottom: 8}}
                              onChange={e => this.doSearch(e.target.value)}
                              value={this.state.filterStr}/>
                <Table
                    showHeader={false}
                    size="small"
                    pagination={false}
                    columns={columns}
                    dataSource={this.state.searchResults}
                    onRow={(record) => {
                        return {
                            onClick: () => {
                                this.selectNode(record.path)
                            }
                        };
                    }}
                />
            </div>
        )
    }
}

export default translate()(Search);
