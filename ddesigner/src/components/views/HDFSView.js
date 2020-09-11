import React, {Component} from 'react';
import {translate} from "react-i18next";
import resource from "../../Resource";
import {Form, Button, Avatar, Tooltip, Row, Input, Modal} from 'antd'
import DisplayList from './DisplayList'
import 'brace/mode/scala'
import 'brace/theme/github'

class HDFSView extends Component {

    constructor(...args) {
        super(...args);
        this.state = {path: "/", file: {children: []}}
    }

    fetchFiles() {
        this.fetchPath(this.normalizePath(this.state.path))
    }

    normalizePath(path) {
        path = path.replace(/\/\//g, '/')
        if (!path || path.length === 0) path = "/"
        else if (path[0] !== '/') path = '/' + path
        if (path.length > 1 && path[path.length - 1] === '/') path = path.substring(0, path.length - 1)
        return path
    }

    getFile(path) {
        return resource.download(this.props.entity, "getFile", {path}, path.replace(/.*\//, ''))
    }

    fetchPath(path) {
        this.setState({path}, () => {
            resource.query(`/api/operation/MetaServer/rt/LivyServer/${this.props.entity.name}/listFiles?path=${this.state.path}`).then(file => {
                this.setState({file})
            }).catch(error => {
                this.setState({file: {children: []}})
            })
        })
    }

    goUp() {
        let path = this.normalizePath(this.state.path)
        let i = path.lastIndexOf("/")
        if (i >= 0) {
            this.fetchPath(this.normalizePath(path.substring(0, i)))
        }
    }


    deleteFile(file) {
        resource.query(`/api/operation/MetaServer/rt/LivyServer/${this.props.entity.name}/deleteFile?path=${this.state.path}/${file}`)
            .then(json => {
                resource.logInfo(JSON.stringify(json))
                this.fetchFiles()
            })
    }

    uploadFile(file) {
        let form = new FormData()
        form.append("path", this.normalizePath(this.state.path))
        form.append("file", file)
        this.setState({fileName: file.name.replace(/\\/g, '/').replace(/.*\//, '')})
        resource.query(`/api/operation/MetaServer/rt/LivyServer/${this.props.entity.name}/uploadFile`, {
            method: 'POST', body: form
        }).then(json => {
            resource.logInfo(JSON.stringify(json))
            this.fetchFiles()
        })
    }

    propsChanged(props) {
        this.fetchFiles()
    }

    componentDidMount() {
        this.propsChanged(this.props)
    }

    componentWillReceiveProps(nextProps) {
        if (nextProps.entity.name !== this.props.entity.name) {
            this.propsChanged(nextProps)
        }
    }

    dformat(d) {
        function pad(number) {
            if (number < 10) {
                return '0' + number;
            }
            return number;
        }

        return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' +
            pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds()) + '.' +
            (d.getMilliseconds() / 1000).toFixed(3).slice(2, 5)
    }

    dfilter(filter, date) {
        return this.dformat(new Date(date)).includes(filter.value.toLowerCase())
    }

    render() {
        const {t} = this.props
        const fileInput = (
            <Tooltip title={t("upload")}>
                <label>
                    <Avatar className="avatar-button-tool-panel" src="images/icon-core/upload.svg"/>
                    <Input type="file" style={{display: "none"}}
                           onChange={e => {
                               const file = e.target.files[0]
                               if (file) {
                                   this.uploadFile(file)
                               }
                           }}
                           onClick={e => {
                               this.setState({fileName: undefined})
                           }}
                    />
                </label>
            </Tooltip>
        )
        return (
            <div>
                <Row>
                    <Form layout={"inline"}>
                        <Form.Item>
                            <Input value={this.state.path} placeholder={"Path"} style={{width: '400px'}}
                                   onChange={e => {
                                       this.setState({path: e.target.value})
                                   }}/>
                        </Form.Item>
                        <Form.Item>
                            <Tooltip title={t("refresh")}>
                                <Button shape={"circle"} style={{border: 0}} onClick={() => this.fetchFiles()}>
                                    <Avatar className="avatar-button-tool-panel"
                                            src={"images/icon-core/refresh-modern.svg"}/>
                                </Button>
                            </Tooltip>
                        </Form.Item>
                        <Form.Item>
                            <Tooltip title={t("up")}>
                                <Button shape={"circle"} style={{border: 0}} onClick={() => this.goUp()}>
                                    <Avatar className="avatar-button-tool-panel" src={"images/icon-core/arrow-up.svg"}/>
                                </Button>
                            </Tooltip>
                        </Form.Item>
                        <Form.Item>
                            <Input addonBefore={fileInput} value={this.state.fileName} readOnly={true}/>
                        </Form.Item>
                    </Form>
                </Row>
                <Row>
                    <DisplayList
                        list={this.state.file.children}
                        storageId={"dd_HDFSView.files"}
                        columns={[
                            {
                                Header: 'rt.HDFSFile.attrs.name.caption',
                                accessor: 'name',
                                show: true,
                                Cell: row => (row.original.type === "DIRECTORY" && <a onClick={() => {
                                    this.fetchPath(this.normalizePath(this.state.path + '/' + row.original.name))
                                }}>{row.original.name}</a>) ||
                                    (row.original.type === "FILE" && <span>{row.original.name}</span>)
                            },
                            {Header: 'rt.HDFSFile.attrs.type.caption', accessor: 'type', show: true},
                            {Header: 'rt.HDFSFile.attrs.owner.caption', accessor: 'owner', show: true},
                            {Header: 'rt.HDFSFile.attrs.group.caption', accessor: 'group', show: true},
                            {Header: 'rt.HDFSFile.attrs.permission.caption', accessor: 'permission', show: true},
                            {Header: 'rt.HDFSFile.attrs.length.caption', accessor: 'length', show: true},
                            {
                                Header: 'rt.HDFSFile.attrs.time.caption', accessor: "modificationTime", show: true,
                                Cell: row => (
                                    <span>{this.dformat(new Date(row.value))}</span>
                                ),
                                minWidth: 110,
                                filterMethod: (filter, row) => this.dfilter(filter, row.modificationTime)
                            },
                        ]}
                        controlColumn={{
                            Cell: row => (
                                <Button.Group className="pull-left">
                                    <Tooltip title={t("download")} placement={'left'}>
                                        <Button type="dashed" size="small" placement="" onClick={() => {
                                            this.getFile(this.normalizePath(this.state.path + '/' + row.original.name))
                                        }}>
                                            <Avatar className="button-avatar" src="images/icon-core/arrow-down.svg"
                                                    size={"small"}/>
                                        </Button>
                                    </Tooltip>
                                    <Tooltip title={t("delete")} placement={'left'}>
                                        <Button type="dashed" size="small" placement="" onClick={() => {
                                            if (row.original.type === "DIRECTORY") {
                                                Modal.confirm({
                                                    content: t("confirmdelete"),
                                                    okText: t("delete"),
                                                    cancelText: t("cancel"),
                                                    onOk: () => {
                                                        this.deleteFile(row.original.name)
                                                    }
                                                })
                                            }
                                            else {
                                                this.deleteFile(row.original.name)
                                            }
                                        }}>
                                            <Avatar className="button-avatar" src="images/icon-core/delete-modern.svg"
                                                    size={"small"}/>
                                        </Button>
                                    </Tooltip>
                                </Button.Group>
                            ),
                            filterable: false,
                            sortable: false,
                            resizable: false,
                            width: 90
                        }}
                    />
                </Row>
            </div>
        )
    }
}

export default translate()(HDFSView);
