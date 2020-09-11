import React, {Component} from 'react';
import {translate} from "react-i18next";
import resource from "../../Resource";
import {
    Row,
    Col,
    Table
} from 'antd'
class VCSInfo extends Component {

    constructor(...args) {
        super(...args);
        this.state = {info: {}}
    }

    getInfo() {
        const entity = Object.assign({}, this.props.activeObject, this.props.entity)
        const {project} = entity
        if (project) {
            const {_type_, name} = entity
            resource.call(project, 'svnProps', {_type_, name}).then(info => {
                this.setState({info})
            })
        }
    }

    componentDidMount() {
        this.getInfo()
    }

    componentDidUpdate(prevProps, prevState) {
        if (this.props.entity !== prevProps.entity) {
            this.getInfo()
        }
    }

    render() {
        return (
            <Row gutter={24}>
                <Col span={2}/>
                <Col span={20}>
                    <Table showHeader={false} pagination={false} columns={[
                        {dataIndex: "key"},
                        {dataIndex: "value"},
                    ]} dataSource={[
                        {key: "Last Commit Author", value: this.state.info.lastCommitAuthor},
                        {key: "Last Changed Date", value: this.state.info.lastChangedDate},
                        {key: "Last Changed Revision", value: this.state.info.lastChangedRevision},
                        {key: "Log Message", value: this.state.info.logMessage},
                    ]}/>
                </Col>
                <Col span={2}/>
            </Row>
        )
    }
}

export default translate()(VCSInfo);
