# Project Information

This file contains important information regarding contributing to the project and reporting security vulnerabilities.

---

## Contributing to NewPass 🚀🫱🏻‍🫲🏼

### 1. Fork the repo

You can do that by clicking this badge: [![Fork](https://img.shields.io/github/forks/6eero/NewPass?logo=github&style=social)](https://github.com/6eero/NewPass/fork)

**[How To Fork Guide by GitHub](https://docs.github.com/en/get-started/quickstart/fork-a-repo)**

### 2. Pick an issue

What do you want to work on? [Choose an issue](https://github.com/6eero/NewPass/issues) that you'd like to contribute to.

#### Workflow:

1. Browse **[NewPass Issues](https://github.com/6eero/NewPass/issues)**.
2. Select or **[create a new issue](https://github.com/6eero/NewPass/issues/new/choose)** that interests you and that you'd like to work to.
3. Comment **`I'm on it`** on the issue.

### 3. Create a feature branch in your fork

For your issue, make a feature branch in your forked NewPass repo.

#### Create a new branch

You can use the GitHub GUI or open a terminal in your cloned forked NewPass repo and run: 
```
git checkout -b fix-issue-YOUR_ISSUE_NUMBER
```

_Replace "YOUR_ISSUE_NUMBER" with the id/number of your issue._

#### Let's Dive In

**🔨 Workflow:**

- Commit your changes.
- Try to follow the [SOLID Principles](https://en.wikipedia.org/wiki/SOLID).
- Build and test regularly to ensure existing features remain intact.
- Make sure your changes don't break anything.

**❓ Ask Yourself:**

- "Is this the simplest approach?"
- "Can I achieve the same with fewer changes?"
- "Does it cover all scenarios?"
- "Is my code robust?"

### 4. Submit a PR to `main` branch

By now, you should have committed your changes to your branch and ensured they function properly on an actual Android device. The last task is to initiate a pull request to merge your changes into the `main` branch of
[NewPass](https://github.com/6eero/NewPass/pulls)

**[How To Submit a PR Guide by GitHub](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/creating-a-pull-request-from-a-fork)**

---

## Reporting a Vulnerability

If you believe you have found a security vulnerability in our software, we encourage you to let us know right away. We will investigate all legitimate reports and do our best to quickly fix the problem.

### Private Contact Method

To securely send a message to the `security@newpass.solutions` email address, you can encrypt your message using the GPG key provided by NewPass.

#### Step 1: Download the GPG Public Key

First, download the GPG public key from the following link:
[NewPass Public Key](https://github.com/6eero/NewPass/blob/master/public_key.asc)

#### Step 2: Import the Public Key

Once the public key is downloaded, import it into your GPG keyring using the following command in your terminal or command prompt:

```
gpg --import public_key.asc
```

#### Step 3: Verify Key Import
Verify that the public key has been imported successfully by running:

```
gpg --list-keys
```

You should see the NewPass public key listed among your imported keys.

#### Step 4: Encrypt Your Message

Now, you can encrypt your message using the NewPass public key. For example, if your message is stored in a file called message.txt, you can use the following command:

```
gpg --encrypt --armor -r security@newpass.solutions < message.txt > encrypted_message.asc
```

This command encrypts your message and saves the encrypted output to a file named encrypted_message.asc.

#### Step 5: Send Encrypted Message
You can now send the encrypted message file (encrypted_message.asc) as an email attachment to security@newpass.solutions. 

### Contact Information

For general inquiries or assistance, please open an issue.
