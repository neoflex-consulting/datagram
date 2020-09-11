import React, {Component} from 'react';
import {Modal, Form, Input, Avatar} from 'antd'
import resource from '../Resource'
import PropTypes from 'prop-types'
import {translate} from 'react-i18next'

class LoginForm extends Component {

    constructor(...args) {
        super(...args);
        this.state = {path: []}
    }

    render() {
        let {t, showLogin, userName, password, setState} = this.props
        return (showLogin &&
            <Modal title={t('login')}
                   visible={showLogin}
                   closable={false}
                   cancelText={t('cancel')}
                   okText={t('login')}
                   onCancel={() => {
                       resource.cancel()
                   }}
                   onOk={(e) => {
                       resource.authenticate(userName, password)
                   }}
            >
                <Form onSubmit={(e) => {
                    e.preventDefault()
                }}>
                    <Form.Item>
                        <Input
                            ref={(input) => {
                                if (input && !this.input) {
                                    this.input = input
                                    setTimeout(()=>{
                                        if (input.input) {
                                            input.focus()
                                        }
                                    }, 0)
                                }
                            }}
                            prefix={
                                <Avatar className="avatar-login" size="small" src='images/icon-core/user-modern.svg'/>
                            }
                            placeholder={t('username')}
                            value={userName}
                            onChange={e => {
                                setState({userName: e.target.value})
                            }}
                            onPressEnter={(e) => {
                                resource.authenticate(userName, password)
                            }}
                        />
                    </Form.Item>
                    <Form.Item>
                        <Input
                            prefix={
                                <Avatar className="avatar-login" size="small" src='images/icon-core/lock-modern.svg'/>
                            }
                            type="password"
                            placeholder={t('password')}
                            value={password}
                            onChange={e => {
                                setState({password: e.target.value})
                            }}
                            onPressEnter={(e) => {
                                resource.authenticate(userName, password)
                            }}
                        />
                    </Form.Item>
                </Form>
            </Modal>
        )
    }
}

LoginForm.propTypes = {
    showLogin: PropTypes.bool.isRequired,
    userName: PropTypes.string,
    password: PropTypes.string,
    setState: PropTypes.func.isRequired
}

export default translate()(LoginForm);
